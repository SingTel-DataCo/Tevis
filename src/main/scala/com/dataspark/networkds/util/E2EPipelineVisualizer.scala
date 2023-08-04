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

class E2EPipelineVisualizer(resolveUnsafeArrayData: Boolean) {

  val log = LogManager.getLogger(this.getClass)
  val unsafeArrayData = "org.apache.spark.sql.catalyst.expressions.UnsafeArrayData"
  import com.dataspark.networkds.util.E2EVariables._

  /**
   * Extracts JSON data. By default it uses DataFrame.toJSON() but if it detects the presence of UnsafeArrayData, it
   * will query for that data and convert to JSON using Jackson's ObjectMapper.
   * Note that we can't completely replace DataFrame.toJSON() as this returns a clean conversion of data excluding its
   * underlying schema, as compared to ObjectMapper which returns too many details.
   * @param ds
   * @param df
   * @return
   */
  def extractDataInJson(df: DataFrame, rowLimit: Int): AnyRef = {
    val list = try {
      df.limit(rowLimit).toJSON.collect()
    } catch {
      case ex: Exception =>
        log.error("Error extracting JSON data from DataFrame. " + ex.getMessage, ex)
        Array[String]()
    }
    if (resolveUnsafeArrayData && list.nonEmpty && list.exists(_.contains(unsafeArrayData))) {
      val rows = list.filter(_.contains(unsafeArrayData))
      val fieldsToReread = rows.flatMap(r => objectMapper.readValue(r, classOf[Map[String, AnyRef]])
        .filter{ case(_, v) => v.toString.contains(unsafeArrayData)}.keys.toSeq).distinct
      val newQuery = df.select(fieldsToReread.map(col): _*).limit(rowLimit).collect()
        .map(x => x.schema.zipWithIndex.map(si => (si._1.name, x(si._2)) ).toMap)

      list.map(i => objectMapper.readValue(i, classOf[Map[String, AnyRef]])).zip(newQuery)
        .map(row => row._1 ++ row._2.keySet.map(cell => cell -> row._1(cell).asInstanceOf[Map[String, Any]]
          .zip(toMap(row._2(cell)))
          .map(getCellDetailMap)))
    } else list.map(objectMapper.readTree)
  }

  /**
   * Converts a row to a Map. The row could be a Map already, or an instance of GenericRowWithSchema.
   * @param row
   * @return
   */
  def toMap(row: Any): Map[String, Any] = {
    if (row.isInstanceOf[Map[String, Any]]) row.asInstanceOf[Map[String, Any]] else {
      val rowWithSchema = row.asInstanceOf[GenericRowWithSchema]
      rowWithSchema.schema.map(_.name).map(x => x -> rowWithSchema.getAs(x)).toMap
    }
  }

  /**
   * Reconciles the cell details recursively from both maps:
   *  (1) one obtained via df.toJSON() which produces UnsafeArrayData, and
   *  (2) one obtained from manually querying the column that had UnsafeArrayData in the first map
   * @param cellEntry
   * @return
   */
  def getCellDetailMap(cellEntry: ((Any, Any), (Any, Any))): (String, Any) = {
    val finalCell = if (cellEntry._1._1.toString.contains(unsafeArrayData)) {
      (cellEntry._2._1.asInstanceOf[mutable.WrappedArray[_]].toList.mkString(", "), cellEntry._1._2)
    }
    else if (cellEntry._1._2.toString.contains(unsafeArrayData) && cellEntry._1._2.isInstanceOf[Map[String, Any]]) {
      (cellEntry._1._1.toString, cellEntry._1._2.asInstanceOf[Map[String, Any]]
        .zip(cellEntry._2._2.asInstanceOf[Map[String, Any]]).map(getCellDetailMap))
    }
    else { cellEntry._1.asInstanceOf[(String, Any)] }
    //if (finalCell.toString.contains(unsafeArrayData))
    //  throw new IllegalStateException("Still contains unsafeArrayData: " + cellEntry.toString)
    finalCell
  }

  def generateVisualization(dir: String): Map[String, Any] = {

    val confPath = E2EVariables.confPath(dir)
    val runnerFolder = dir + "/planner/"
    val allRunners = E2EConfigUtil.getAllModuleRunners(runnerFolder)
    val unitLevelConfigMap = allRunners.groupBy(_.profiles(0)).map(x => x._1 -> x._2.flatten(_.configFilesNeeded).toSet.toSeq)
    val moduleConfs = E2EConfigUtil.getAllModuleConfsFormattedPaths(confPath, unitLevelConfigMap)
    val nodes = E2EConfigUtil.getAllModuleNodes(dir, allRunners, moduleConfs)

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
          log.info("Reading " + fullPath)
          val data = Str.parseSeqStringToMap(fullPath, ",")
          generateMapOutput(p, data, "CSV")
        }
        else {
          log.info("Skipping reading " + p._1)
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
        .map(x => configFormatter.format("${Hdfs." + x + "}")))

    log.info("Done generating pipeline-withdata.json!")
    jsonMap
  }
}
