/*
 * Copyright © DataSpark Pte Ltd 2014 - 2022.
 *
 * This software and any related documentation contain confidential and proprietary information of
 * DataSpark and its licensors (if any). Use of this software and any related documentation is
 * governed by the terms of your written agreement with DataSpark. You may not use, download or
 * install this software or any related documentation without obtaining an appropriate licence
 * agreement from DataSpark. All rights reserved.
 */

package com.dataspark.networkds.util

import com.dataspark.networkds.util.ModuleState.StateVal
import com.fasterxml.jackson.databind.JsonNode
import org.apache.log4j.LogManager
import org.apache.spark.SparkException
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.{AnalysisException, DataFrame, SparkSession}

import java.io._
import java.util
import scala.collection.JavaConverters._
import scala.collection.immutable.{ListMap, Map}
import scala.collection.mutable

case class ModuleConf(name: String, var inputPaths: Seq[String] = Seq.empty[String],
  var outputPaths: Seq[String] = Seq.empty[String], configs: Map[String, AnyRef] = Map.empty[String, AnyRef],
  var confFilePath: String = "", var includes: Seq[String] = Seq.empty[String])

object ModuleState extends Enumeration{
  sealed case class StateVal(name: String, color: String) extends Val(name) {
    override def toString(): String = this.name
  }
  val Start = StateVal("Start", "lightgreen")
  val Normal = StateVal("Normal", "lightblue")
  val End = StateVal("End", "pink")
  val Disconnected = StateVal("Disconnected", "yellow")
  val Deprecated = StateVal("Deprecated", "red")

}

case class ModuleNode(id: Int, runner: ModuleRunner, var appConf: ModuleConf,
  var upstream: java.util.List[Edge] = new util.ArrayList[Edge](),
  var downstream: java.util.List[Edge] = new util.ArrayList[Edge](),
  var state: ModuleState.StateVal = ModuleState.Deprecated) {

  def downstreamLinksToGojsFormat(): Seq[Map[String, Any]] = {
    downstream.asScala.map(e =>
      Map("id" -> ((e.from.id * 100) + e.to.id), "from" -> e.from.id, "fromName" -> e.from.appConf.name,
        "to" -> e.to.id, "toName" -> e.to.appConf.name, "text" -> e.link.replaceAll(",", "\\\n"))
    )
  }

  def nodeToGojsFormat(count: Int) : Map[String, Any] = {
    Map("id" -> count, "text" -> appConf.name, "color" -> state.color,
      "level" -> (if (runner != null) runner.profiles(0) else null))
  }
}

case class Edge(from: ModuleNode, to: ModuleNode, link: String) {
  override def toString: String = {
    from.appConf.name + " -> " + link + " -> " + to.appConf.name
  }
}

class E2EPipelineVisualizer() {

  val log = LogManager.getLogger(this.getClass)

  def generateVisualization(dir: String): Map[String, Any] = {

    val confPath = E2EVariables.confPath(dir)
    val runnerFolder = dir + "/planner/"
    val allRunners = E2EConfigUtil.getAllModuleRunners(runnerFolder)
    val unitLevelConfsNeededMap = allRunners.groupBy(_.profiles(0)).map(x => x._1 -> x._2.flatten(_.configFilesNeeded).distinct)
    val nodes = E2EConfigUtil.getAllModuleNodesAndConfs(dir, allRunners, unitLevelConfsNeededMap)

    val moduleRunnerNodes = nodes.filter(_.id > 0).filterNot(_.runner.disabled)

    // Assign states on the nodes based on their upstream, downstream and runner states.
    // Default state is "Deprecated", which means a node is not part of run_pipeline script (doesn't have a ModuleRunner).
    moduleRunnerNodes.foreach(x => x.state = ModuleState.Normal)
    val startNodes = moduleRunnerNodes.filter(n => n.upstream.isEmpty && !n.downstream.isEmpty)
    startNodes.foreach(x => x.state = ModuleState.Start)
    val endNodes = moduleRunnerNodes.filter(n => !n.upstream.isEmpty && n.downstream.isEmpty)
    endNodes.foreach(x => x.state = ModuleState.End)
    val disconnectedNodes = moduleRunnerNodes.filter(n => n.upstream.isEmpty && n.downstream.isEmpty)
    disconnectedNodes.foreach(x => x.state = ModuleState.Disconnected)

    // Load the Config renderer for formatting the placeholders in .conf files
    val configFormatter = new E2EConfigUtil(new File(confPath).listFiles()
      .filter(f => f.getName.endsWith(".conf") && !f.getName.endsWith("RunConfig.conf") && !f.getName.startsWith("HybridApproach")).map(_.getAbsolutePath))

    val outPathPrefix = configFormatter.format("${Hdfs.outputBasePath}")
    def trimRootPath(path: String): String = path.replaceAll(outPathPrefix, "")

    // Read all the input and output paths and obtain their schema and sample data
    def generateMapOutput(pathPair: (String, String), df: Seq[Map[String, Any]], dType: String): (String, Map[String, AnyRef]) = {
      if (df == null) throw new IllegalStateException(pathPair._2 + ": df is null")
      (pathPair._1, Map("format" -> dType, "path" -> pathPair._2,
        "schema" -> (if (df.isEmpty) Map.empty[String, Any] else df.head.keySet.map(x =>
          x -> Map("type" -> "String", "nullable" -> "true")).toMap),
        "data" -> df))
    }

    val allDirsRecursive = moduleRunnerNodes.flatMap(m => (m.appConf.inputPaths ++ m.appConf.outputPaths)
      .map(p => p.substring(p.indexOf("=") + 1).replaceAll("\\[|\\]", "").trim).toSet).toSet[String]
      .map(p => (p, configFormatter.format(p)))
      .flatten(p => {
        val dir = new File(p._2)
        if (!dir.exists() || dir.isFile) Seq(p) else {
          val childDirs = dir.listFiles().filter(_.isDirectory)
          if (childDirs.nonEmpty) childDirs.map(f => (f.toString, f.toString)) else Seq((p._1, p._2))
        }
      })
      //.filter(_._1 == "output//usage_allocation/post_prediction/result")
      //parallel reading of the datasets will be done here
      val dataDetails = allDirsRecursive.par
      .map(p => {
        val fullPath = confPath + p._2
        if (p._2.endsWith(".csv") && !p._2.contains("/") && new File(fullPath).exists()) {
          (fullPath, Map.empty)
        }
        else {
//          log.info("Skipping reading " + p._1)
          (p._1, Map.empty)
        }
      }).seq.toMap

    val allNodes = Seq(E2EConfigUtil.makeStartNode(nodes)) ++ nodes
    val enabledRunnerNodes = allNodes.filter(_.id >= 0)
    val profiles = Map("sector" -> enabledRunnerNodes.filter(_.runner.profiles.contains("sector")).map(_.id),
      "site" -> enabledRunnerNodes.filter(_.runner.profiles.contains("site")).map(_.id),
      "cluster" -> enabledRunnerNodes.filter(_.runner.profiles.contains("cluster")).map(_.id))

    // Construct the final pipeline.json file
    val runnerJsonMap = ListMap(allNodes.map(n => (n.id, Map("sequenceNo" -> n.id,
      "runner" -> {if (n.runner == null) n.runner else Map(
        "shellScript" -> n.runner.shellScript, "entryPointClass" -> n.runner.entryPointClass,
        "configFiles" -> n.runner.configFilesNeeded, "appConf" -> n.runner.appConf,
        "type" -> (if (n.runner.runnerType != null) n.runner.runnerType.toString else null))},
      "appConf" -> n.appConf.copy(name = n.appConf.name,
        inputPaths = n.appConf.inputPaths.map(configFormatter.format),
        outputPaths = n.appConf.outputPaths.map(configFormatter.format),
        configs = n.appConf.configs.map(c => c._1 -> configFormatter.format(c._2))),
      "upstream" -> n.upstream.asScala.map(e => e.from.appConf.name),
      "downstream" -> n.downstream.asScala.map(e => e.to.appConf.name),
      "state" -> n.state.name,
      "profiles" -> (if (n.runner == null) Seq.empty else n.runner.profiles)))): _*)

    val jsonMap = Map(
      "version" -> E2EConfigUtil.getVersion(runnerFolder),
      "nodeStates" -> ModuleState.values.asInstanceOf[Set[StateVal]].map(x => (x.name, x.color)).toMap,
      "moduleNodes" -> allNodes.map(x => x.nodeToGojsFormat(x.id)),
      "links" -> allNodes.flatMap(x => x.downstreamLinksToGojsFormat())
        .map(x => x + ("text" -> trimRootPath(x("text").toString))),
      "runners" -> runnerJsonMap,
      "datasets" -> dataDetails,
      "profiles" -> profiles,
      "rootDirs" -> Seq("input", "outputSector", "outputSite", "outputCluster")
        .map(x => configFormatter.format("${Hdfs." + x + "}")),
      "confDir" -> confPath)

    log.info("Done generating pipeline-withdata.json!")
    jsonMap
  }
}
