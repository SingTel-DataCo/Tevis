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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.IOUtils
import org.apache.commons.text.StringSubstitutor
import org.apache.log4j.LogManager

import java.io._
import java.nio.file.Paths
import java.util.regex.Pattern
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

class E2EConfigUtil(confFiles: Seq[String]) {

  updateConfigFiles(confFiles)
  val propertiesMap = ConfigFactory.load().entrySet().asScala.map(x => (x.getKey, x.getValue.unwrapped())).toMap
  val substitutor = new StringSubstitutor(propertiesMap.asJava)

  def format(text: String): String = {
    substitutor.replace(text)
  }

  def updateConfigFiles(configFilesNeeded: Seq[String]): Unit = {

    val tmpOutPath = File.createTempFile("application", ".conf")
    // Merge all config files needed into one big application.conf file. Also look into some simplified config values
    // in e2e_pipeline_sim/conf folder that will be suitable for unit testing.
    joinFiles(tmpOutPath, configFilesNeeded.map(new File(_)))

    // Set the config.file of the currently running program so that it gets picked up during ConfigFactory.load(),
    // of course with the help of ConfigFactory.invalidateCaches() function
    System.setProperty("config.file", tmpOutPath.getPath)
    // This force-reloads the system properties, to include the newly added configs via config.file property.
    // See link for more details: https://github.com/lightbend/config#standard-behavior
    ConfigFactory.invalidateCaches()
  }

  def joinFiles(destination: File, sources: Seq[File]): Unit = {
    val output = new BufferedWriter(new FileWriter(destination, true))
    for (source <- sources) {
      val input = new BufferedReader(new FileReader(source))
      val lines = Stream.continually(input.readLine()).takeWhile(_ != null).filter(!_.startsWith("include"))
      lines.foreach(x => output.write(x + "\n"))
    }
    output.close()
  }
}

object E2EVariables {

  val relativeConfPath = "conf/"
  val objectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
    //.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def confPath(rootPath: String): String = {
    val f = new File(rootPath + "/" + relativeConfPath)
    if (!f.exists()) throw new FileNotFoundException("Path doesn't exist: " + f.getAbsolutePath)
    getConfigPath(new File(rootPath + "/" + relativeConfPath).getCanonicalFile)
  }

  def getConfigPath(file: File): String = {
    if (file.getCanonicalFile.getParentFile.getName != "planner") file.toURI.getPath
    else getConfigPath(file.getParentFile.getParentFile.toPath.resolve(Paths.get(file.getName)).toFile)
  }

  val csvFilter = new FilenameFilter() {
    def accept(dir: File, fileName: String): Boolean = fileName.endsWith(".csv")
  }
}

object E2EConfigUtil {

  val log = LogManager.getLogger("E2EConfigUtil")

  def getAllModuleRunners(mainRunnerScriptFile: String): Seq[ModuleRunner] = {

    val modules = IOUtils.readLines(new FileReader(mainRunnerScriptFile))
      .asScala.filter(_.matches(".*bin.*\\.sh.*")).map(_.trim)
    val rootDir = new File(mainRunnerScriptFile).getParentFile.getAbsolutePath
    modules.zipWithIndex.flatMap { case (m, i) =>
      val disabled = m.startsWith("#")
      val shFile = "\\..*bin.*\\.sh".r.findFirstIn(m).get
      log.info(s"[$i] Parsing ${ if (disabled) "disabled " else "" }runner script $shFile")
      val lines = IOUtils.readLines(new FileReader(rootDir + "/" + shFile)).asScala
      val appConfigs = lines.filter(_.contains("render_configs.py"))
      val classes = lines.filter(_.startsWith("--class")).map("com.dataspark.[\\w|\\.]+".r.findFirstIn(_).get)
      val moduleRunners = lines.filter(_.trim.startsWith("RESOURCES=")).zipWithIndex.map { case (r, rIdx) => {
        val configFilesTmp = Map("Hdfs.conf" -> "HdfsMain.conf", "Field.conf" -> "FieldMain.conf")
          .foldLeft(r){case (z, (s, r)) => z.replaceAll(s, r)}
        val configFilesNeeded = "\\w+.conf".r.unanchored.findAllIn(configFilesTmp).toSeq
        // Extract the entry-point class in the script indicated with the --class parameter
        val entryPointClass = classes(rIdx)
        val appConf = appConfigs(rIdx)
        val appConfModuleName = getAppConf(appConf)
        val appConfSectionName = if (appConf.contains("-m")) appConf.substring(appConf.indexOf("-m") + 3) else ""
        val confFile = if (appConfSectionName.isEmpty || appConfModuleName == appConfSectionName)
          appConfModuleName else appConfModuleName + "_" + appConfSectionName
        ModuleRunner(shFile, entryPointClass, configFilesNeeded, confFile, disabled = disabled)
      }
      }
      if (moduleRunners.isEmpty) {
        val appConf = if (appConfigs.nonEmpty) getAppConf(appConfigs(0)) else
          shFile.replaceAll(".*run_|.sh", "").capitalize
        Seq(ModuleRunner(shFile, "", Seq.empty[String], appConf, RunnerType.Python, disabled))
      } else moduleRunners
    }
  }

  def getAppConf(line: String): String = line.substring(line.indexOf("-cf") + 8, line.indexOf(".conf"))

  /**
   * Parse all .conf files
   */
  def getAllModuleConfsFormattedPaths(dir: String, formatter: E2EConfigUtil): Seq[ModuleConf] = {
      val moduleConfs = new File(E2EVariables.confPath(dir)).listFiles().filter(_.getName.endsWith(".conf")).flatMap(f => {
      val moduleName = f.getName.replaceAll(".conf", "")
      val lines = IOUtils.readLines(new FileReader(f)).asScala
      val sectionHeads = lines.zipWithIndex.filter(x => x._1.matches("\\w+\\s*\\{"))
      val sectionIndexes = sectionHeads.map(_._2)
      val sections = sectionIndexes.zip(sectionIndexes.slice(1, sectionHeads.length) ++ Seq(lines.length))
      val modConfListWithinAFile = sections.map { case (start, end) =>
        val sectionLines = lines.slice(start, end)
        val sectionName = sectionLines(0).replaceAll("\\{", "").trim
        if (sectionName.equals("DataJoin")) extractModuleConfDataJoin(moduleName, sectionName, sectionLines)
        else if (sectionName.equals("DecisionEngine"))
          extractModuleConfDecisionEngine(moduleName, sectionName, sectionLines, formatter)
        else extractModuleConf(moduleName, sectionName, sectionLines)
      }
      modConfListWithinAFile
    }).filter(_.inputPaths.nonEmpty)
    formatPaths(moduleConfs, formatter)
  }

  /**
   * Replace placeholders with actual path values
   * @param confs
   * @return
   */
  def formatPaths(confs: Seq[ModuleConf], formatter: E2EConfigUtil): Seq[ModuleConf] = {
    confs.map(c => ModuleConf(c.name, c.inputPaths.map(formatter.format), c.outputPaths.map(formatter.format), c.configs.map(formatter.format)))
  }

  def generateConfFromShell(node: ModuleNode, dir: String, formatter: E2EConfigUtil): ModuleNode = {
    val runner = node.runner
    val text = IOUtils.readLines(new FileReader(dir + runner.shellScript)).asScala.mkString(" ")
    var inPaths: Seq[String] = null
    var outPaths: Seq[String] = null
    if (runner.shellScript.contains("sectorClustering")) {
      val pattern = ".*getProperty.py.*?-p\\s(.*?)\\s\\|.*".r
      val pattern(input) = text
      inPaths = input.split("\\s+").map(m => "input=" + formatter.format("${" + m + "}"))
      node.appConf = ModuleConf(runner.appConf, node.appConf.inputPaths ++ inPaths, node.appConf.outputPaths)
    }
    else if (runner.shellScript.contains("getmerge")) {
      if (text.contains("declare")) {
        val pattern = ".*declare.*?inputs=\\((.*?)\\).*declare.*?outputs=\\((.*?)\\).*".r
        val pattern(input, output) = text
        inPaths = input.split("\\s+").map(m => "input=" + formatter.format("${Outputs." + m + "}"))
        outPaths = output.split("\\s+").map(m => "output=" + formatter.format("${Local." + m + "}"))
      } else {
        inPaths = "-i\\s(\\w+\\.\\w+)".r.findAllMatchIn(text)
          .map(m => "input=" + formatter.format("${" + m.group(1) + "}")).toSeq
        outPaths = "-o\\s(\\w+\\.\\w+)".r.findAllMatchIn(text)
          .map(m => "output=" + formatter.format("${" + m.group(1) + "}")).toSeq
      }
      node.appConf = ModuleConf(runner.appConf, inPaths.distinct, outPaths.distinct)
    } else if (runner.shellScript.contains("decisionEngine")) {
      if (text.contains("getProperty.py")) {
        val pattern = ".*getProperty.py.*?-p\\s(.*?)\\s\\|.*getProperty.py.*?-p\\s(.*?)\\s\\|.*".r
        val pattern(input, output) = text
        inPaths = input.split("\\s+").map(m => "input=" + formatter.format("${" + m + "}"))
        outPaths = output.split("\\s+").map(m => "output=" + formatter.format("${" + m + "}"))
      } else {
        inPaths = "-i\\s(\\w+\\.\\w+)".r.findAllMatchIn(text).map(m => "input=" + formatter.format("${" + m.group(1) + "}")).toSeq
        outPaths = "-o\\s(\\w+\\.\\w+)".r.findAllMatchIn(text).map(m => "output=" + formatter.format("${" + m.group(1) + "}")).toSeq
      }
      node.appConf.inputPaths = node.appConf.inputPaths ++ inPaths.distinct
      node.appConf.outputPaths = node.appConf.outputPaths ++ outPaths.distinct
    }
    node
  }

  /**
   * Generate a list of ModuleNodes each composed of the pair of ModuleRunner and its ModuleConf
   */
  def getAllModuleNodes(moduleConfs: Seq[ModuleConf], dir: String, formatter: E2EConfigUtil): Seq[ModuleNode] = {
    val rootDir = dir + "/planner/"
    val sectorLevelRunners = getAllModuleRunners(rootDir + "run_pipeline_sector_level.sh")
    val siteLevelRunners = getAllModuleRunners(rootDir +"run_pipeline_additional_run_site_level.sh")
    val clusterLevelRunners = getAllModuleRunners(rootDir + "run_pipeline_additional_run_cluster_level.sh")
    val allRunners = (sectorLevelRunners ++ siteLevelRunners ++ clusterLevelRunners).distinct
    sectorLevelRunners.foreach(r => allRunners.find(_.equals(r)).get.profiles += "sector")
    siteLevelRunners.foreach(r => allRunners.find(_.equals(r)).get.profiles += "site")
    clusterLevelRunners.foreach(r => allRunners.find(_.equals(r)).get.profiles += "cluster")
    val moduleRunners = allRunners.filterNot(_.disabled).zip(Stream.from(1)) // ++ siteLevelRunners
    val moduleRunnersDisabled = allRunners.filter(_.disabled).zip(Stream.from(-201, -1))
    val runnerNodes = moduleRunners.map(r => ModuleNode(r._2, r._1, moduleConfs.find(mc => mc.name.equals(r._1.appConf))
      .getOrElse(ModuleConf(r._1.appConf)))).map(r => generateConfFromShell(r, rootDir, formatter))
    val runnerNodesDisabled = moduleRunnersDisabled.map(r => ModuleNode(r._2, r._1, moduleConfs.find(_.name.equals(r._1.appConf))
      .getOrElse(ModuleConf(r._1.appConf))))
    val runnerMap = (moduleRunners ++ moduleRunnersDisabled).map(r => (r._1.appConf, r)).toMap
    val noRunnerNodes = moduleConfs.zipWithIndex.filter(c => !runnerMap.contains(c._1.name))
      .map(mc => ModuleNode((mc._2 * -1) - 1, null, mc._1))
    val nodes = runnerNodes ++ runnerNodesDisabled ++ noRunnerNodes

    // Search for upstream and downstream modules of each ModuleNode
    nodes.foreach(from => nodes.filter(!_.appConf.name.equals(from.appConf.name)).foreach(to => {
      if (getIntersectingValues(from.appConf.outputPaths, to.appConf.inputPaths).nonEmpty) {
        val path = getIntersectingValues(from.appConf.outputPaths, to.appConf.inputPaths).mkString(", ")
        from.downstream.add(Edge(from, to, path))
      }
      else if (getIntersectingValues(from.appConf.inputPaths, to.appConf.outputPaths).nonEmpty) {
        val path = getIntersectingValues(from.appConf.inputPaths, to.appConf.outputPaths).mkString(", ")
        // Invert the "to" and "from" positions to properly represent upstream module going down to this module
        from.upstream.add(Edge(to, from, path))
      }
    }))
    nodes
  }

  /**
   * Before calling this method, make sure that there are ModuleStates assign to each ModuleNode.
   * @param nodes
   * @return
   */
  def makeStartNode(nodes: Seq[ModuleNode]): ModuleNode = {
    val startNodeDetails = nodes.map(n => (n, n.appConf.inputPaths.filter(p => p.contains("icformat"))))
      .filter(n => n._2.nonEmpty && n._1.state == ModuleState.Start)
      .collect { case (dNodes, inPaths) => (dNodes, inPaths) }.groupBy(_._1.id)
      .map(x => (x._2.map(_._1.id).head, x._2.flatten(_._2)))
    val startNodeRunner = ModuleRunner("", "", Seq(), "", null, false, ListBuffer("sector", "site", "cluster"))
    val startNode = ModuleNode(0, startNodeRunner, ModuleConf("Start", Seq(), startNodeDetails.flatten(_._2).toSet.toSeq, Seq()),
      state = ModuleState.Start)
    startNodeDetails.foreach(d => startNode.downstream.add(Edge(startNode, nodes.find(_.id == d._1).get, d._2.mkString(", "))))
    startNode
  }

  def removeCommentsFromKvPairs(kvPairs: Seq[String]): Seq[String] = {
    kvPairs.filter(_.contains("=")).map(_.split("="))
      .map(x => (x(0), x(1).replaceAll("#.*|\\/\\/.*", "").trim))
      .filter(_._2.nonEmpty).map(x => x._1 + "= " + x._2)
  }

  def extractModuleConf(moduleName: String, sectionName: String, sectionLines: Seq[String]): ModuleConf = {

    val (_, paths) = sectionLines.map(_.trim.replaceAll("\"", ""))
      .span(x => !x.startsWith("# input") && !x.startsWith("// input"))
    val (input, tmpOutput) = paths.span(x => !x.trim.startsWith("# output") && !x.startsWith("// output"))
    val (output, configs) = tmpOutput.span(x => !x.trim.startsWith("# config") && !x.startsWith("// config"))

    ModuleConf(if (moduleName.equals(sectionName)) moduleName else (moduleName + "_" + sectionName),
      removeCommentsFromKvPairs(input),
      removeCommentsFromKvPairs(output),
      configs.filter(_.contains("=")).map(_.replaceAll("#.*", "").trim).filter(_.nonEmpty))
  }

  def extractMatchingText(x: String, pattern: Pattern): String = {
    val matcher = pattern.matcher(x)
    if (matcher.find) matcher.group(1) else x
  }

  def extractModuleConfDataJoin(moduleName: String, sectionName: String, sectionLines: Seq[String]): ModuleConf = {

    val input = sectionLines.map(_.replaceAll("//.*", ""))
      .filter(_.contains("inputPath =")).map(_.trim.replaceAll("\"", ""))
    val output = sectionLines.map(_.replaceAll("//.*", ""))
      .filter(r => r.contains("csvOutputPath =") || r.contains("HdfsOutput =")).map(_.trim)
    val pattern = Pattern.compile("(inputPath =.*?),")
    ModuleConf(if (moduleName.equals(sectionName)) moduleName else (moduleName + "_" + sectionName),
      removeCommentsFromKvPairs(input.map(x => extractMatchingText(x, pattern))),
      removeCommentsFromKvPairs(output),
      Seq.empty[String])
  }

  def extractModuleConfDecisionEngine(moduleName: String, sectionName: String,
    sectionLines: Seq[String], formatter: E2EConfigUtil): ModuleConf = {

    val input = sectionLines.map(_.replaceAll("//.*", ""))
      .filter(r => r.contains("_path") && !r.contains("output_path")).map(_.trim)
    val optimiserPath = input.filter(_.contains("optimiser.conf")).map(formatter.format)
      .toSeq(0).split("=")(1).trim.replaceAll("\"", "")
    val output = sectionLines.map(_.replaceAll("//.*", "")).filter(_.contains("output_path = ")).map(_.trim)
    val optimiserPaths = try {
      IOUtils.readLines(new FileReader(optimiserPath)).asScala
        .map(_.replaceAll("//.*", ""))
        .filter(r => r.contains("filePath")).map(r => "optimiser." + r.trim)
    } catch { case _ => Seq.empty[String] }
    ModuleConf(if (moduleName.equals(sectionName)) moduleName else (moduleName + "_" + sectionName),
      removeCommentsFromKvPairs(input ++ optimiserPaths),
      removeCommentsFromKvPairs(output),
      Seq.empty[String])
  }

  def getIntersectingValues(kvPairs1: Seq[String], kvPairs2: Seq[String]): Seq[String] = {
    val kvPairs1Paths = kvPairs1.map(_.split("=", 2)(1).replaceAll("\\[|\\]", "").trim).filterNot(_.isEmpty)
    val kvPairs2Paths = kvPairs2.map(_.split("=", 2)(1).replaceAll("\\[|\\]", "").trim).filterNot(_.isEmpty)
    (kvPairs1Paths.map(p1 => kvPairs2Paths
      .map(p2 => if (p2.contains(p1)) p2 else "")
      .find(_.nonEmpty).orElse(Some("")).get)
      .filter(_.nonEmpty) ++
      kvPairs2Paths.map(p2 => kvPairs1Paths
        .map(p1 => if (p1.contains(p2)) p1 else "")
        .find(_.nonEmpty).orElse(Some("")).get)
        .filter(_.nonEmpty)).distinct
  }

  def getVersion(dir: String): String = {
    val rootDir = dir + "/planner/"
    val versionProperty = IOUtils.readLines(new FileReader(rootDir + "runner_configs"))
      .asScala.find(_.trim.startsWith("VERSION")).get
    versionProperty.split("=")(1).replaceAll("\"", "")
  }

  //remove the protocol (e.g. s3:, file:) if it exists
  def trimProtocol(dsPath: String): String =
    (if (dsPath.contains(":")) dsPath.substring(dsPath.indexOf(":") + 1) else dsPath)
      .replaceAll("/$", "").replaceAll("//", "/")
}
