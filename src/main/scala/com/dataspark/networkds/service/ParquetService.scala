package com.dataspark.networkds.service

import com.dataspark.networkds.beans.{DsFile, HFile, HFileData}
import com.dataspark.networkds.config.SparkConfig
import com.dataspark.networkds.util.{DatasetUtil, E2EConfigUtil, E2EPipelineVisualizer, E2EVariables, SparkHadoopUtil, Str}
import org.apache.commons.io.FilenameUtils
import org.apache.hadoop.fs.{FileStatus, FileSystem, Path}
import org.slf4j.LoggerFactory
import org.apache.spark.SparkException
import org.apache.spark.sql.{DataFrame, Encoders, Row, SparkSession}
import org.springframework.beans.factory.annotation.{Autowired, Value}
import org.springframework.stereotype.Service

import java.io.{File, FileInputStream, FileNotFoundException, InputStream}
import java.sql.{SQLException, Timestamp}
import scala.collection.immutable
import scala.collection.immutable.ListMap
import scala.collection.mutable.ListBuffer

@Service
class ParquetService {

  val log = LoggerFactory.getLogger(this.getClass.getSimpleName)

  @Autowired
  private var cache: CacheService = _

  @Autowired
  private var fileService: FileService = _

  @Value("${query.row.limit}")
  var rowLimit: Int = _

  @Autowired
  var sparkConfig: SparkConfig = _

  var e2eVisualizer = new E2EPipelineVisualizer()

  var mySparkSession: SparkSession = null

  def spark: SparkSession = {
    if (mySparkSession == null || mySparkSession.sparkContext.isStopped) {
      mySparkSession = sparkConfig.spark
      SparkSession.setDefaultSession(mySparkSession)
      initDsFilesTable()
    }
    mySparkSession
  }

  def fs = {
    FileSystem.get(SparkHadoopUtil.newConfiguration(spark.sparkContext.getConf))
  }

  def initDsFilesTable(): Unit = {
    spark.createDataFrame(spark.sparkContext.parallelize(cache.files.get().dirs.flatten(d => {
      d._2.map(f => Row(f.filename, f.date, f.size, d._1))
    }).toSeq), Encoders.product[DsFile].schema)
      .createOrReplaceTempView("dsfiles")

    val capexDags = fileService.readCapexDags()
    if (capexDags.nonEmpty) {
      spark.read.json(spark.sparkContext.parallelize(capexDags))
        .createOrReplaceTempView("capexDirs")
    }
  }

  def loadFile(hfile: HFile, refresh: Boolean = false): DataFrame = {
    log.info(s"Reading ${hfile.path}")
    if (!refresh && spark.catalog.tableExists(hfile.table)) {
      spark.read.table(hfile.table)
    } else {
      val df = if (hfile.path.toLowerCase.contains("csv")) readCsvFile(hfile)
      else {
        try {
          spark.read.parquet(hfile.path)
        }
        catch {
          case ex: SparkException =>
            log.warn(ex.getMessage)
            readCsvFile(hfile)
        }
      }
      df.createOrReplaceTempView(hfile.table)
      hfile.schema = getSchema(df)
      df
    }
  }

  def dropView(table: String): Unit = {
    spark.sql("DROP TABLE IF EXISTS " + table)
  }

  def readCsvFileLocal(f: File): HFile = {
    val tableName = generateUniqueTableName(f.getName, "")
    Str.parseSeqStringToDF(spark, f.getPath, ",").createOrReplaceTempView(tableName)
    HFile(f.getPath, f.length(), tableName, "csv")
  }

  def readCsvFile(hfile: HFile): DataFrame = {
    val tmpDf = spark.read.options(Map("inferSchema" -> "true", "header" -> "true")).csv(hfile.path)
    hfile.format = "csv"
    tmpDf
  }

  def getSchema(df: DataFrame): ListMap[String, Any] = {
    ListMap(df.schema.map(x =>
      (x.name, Map("type" -> x.dataType.simpleString, "nullable" -> x.nullable))): _*)
  }

  def readSchemaAndData(hFile: HFile): HFileData = {
    val df = loadFile(hFile)
    cache.files.get().dirs.put(hFile.path, listFiles(hFile.path))
    cache.files.save()
    val sql = s"SELECT * FROM ${hFile.table} LIMIT $rowLimit"
    HFileData(data = DatasetUtil.collect(df.limit(rowLimit)), format = hFile.format,
      path = hFile.table, schema = hFile.schema, sql = sql)
  }

  def queryTable(sql: String): HFileData = {
    validateSql(sql)
    val tokens = sql.split(";")
    val df = if (sql.startsWith("%scala")) { runScalaCode(sql.replaceAll("%scala", "")) }
      else {
        var df2 = spark.sql(tokens(0))
        if (tokens.length > 1) {
          val command = tokens(1).trim.toLowerCase()
          df2 = if (command == "summary") df2.summary() else if (command == "describe") df2.describe() else df2
        }
        val customLimit = "(?i)LIMIT\\s+(\\d+)".r.findAllMatchIn(sql).toSeq
        val limit = if (customLimit.isEmpty) rowLimit else customLimit.last.group(1).toInt
        df2.limit(limit)
      }
    HFileData(data = DatasetUtil.collect(df), format = null,
      path = null, schema = getSchema(df), sql = sql)
  }

  // Inspired by this: https://medium.com/@kadirmalak/compile-arbitrary-scala-code-at-runtime-using-reflection-with-variable-injection-2002e0500565
  def runScalaCode(code: String): DataFrame = {
    import tools.reflect.ToolBox
    val tb = reflect.runtime.currentMirror.mkToolBox()
    val tree = tb.parse(
      s"""
         |def wrapper(context: Map[String, Any]): Any = {
         |  val spark = context("spark").asInstanceOf[org.apache.spark.sql.SparkSession]
         |  import org.apache.spark.sql.functions._
         |  import spark.implicits._
         |  $code
         |}
         |wrapper _
        """.stripMargin)

    val f = tb.compile(tree)
    val wrapper = f().asInstanceOf[Map[String, Any] => DataFrame]
    val df = wrapper(Map("spark" -> spark))
    df
  }

  private def validateSql(sql: String): Unit = {
  //  if ("(?i)DROP\\s+TABLE".r.findAllMatchIn(sql).nonEmpty)
  //    throw new SQLException("DROP TABLE command is not allowed.")
    if (sql.contains(";")) {
      val postDfCommand = ";(.+)".r.findAllMatchIn(sql).toSeq
      if (postDfCommand.nonEmpty) {
        val command = postDfCommand.head.group(1).trim.toLowerCase
        if (!Seq("summary", "describe").contains(command))
           throw new SQLException(s"$command: invalid Spark DataFrame function.")
      }
    }
  }

  def generateUniqueTableName(dsPath: String, rootPath: String): String = {
    val tblName = generateTableName(dsPath, rootPath)
    val existingTbls = cache.getAllExistingTablesStartingWith(tblName)
    if (existingTbls.isEmpty) tblName else {
      val tblIndices = existingTbls.map(_.replace(tblName, ""))
        .filter(x => x.nonEmpty && !x.matches("\\d+"))
        .map(x => if (x.isEmpty) 0 else x.replace("_", "").toInt)
      val max = if (tblIndices.isEmpty) 0 else tblIndices.max
      tblName + "_" + (max + 1)
    }
  }

  /**
   * If both dsPath and rootPath are the same then the leaf node (folder or filename) will be used;
   * Otherwise dsPath will be trimmed by the rootPath and the remaining series of child folders will be used
   * @param dsPath
   * @param rootPath
   * @return
   */
  def generateTableName(dsPath: String, rootPath: String): String = {

    val dsPath2 = E2EConfigUtil.trimProtocol(FilenameUtils.separatorsToUnix(dsPath))
    val rootPath2 = E2EConfigUtil.trimProtocol(FilenameUtils.separatorsToUnix(rootPath))

    if (dsPath2 == rootPath2) dsPath2.substring(dsPath2.lastIndexOf("/") + 1)
    else dsPath2.substring(dsPath2.indexOf(rootPath2) + rootPath2.length)
      .replace("sub_module=", "").replace("module=", "")
      .replaceAll("\\/|=|\\+|-|\\.|<|>|%|\\*", " ")
      .trim.replaceAll("\\s+", "_")
  }

  def listFiles(rootDir: String): Seq[DsFile] = {
    val rootPath = new Path(rootDir)
    val csvFile = new File(rootDir) //must be a local CSV file, just in case Hadoop path doesn't exist

    if (fs.exists(rootPath) && fs.isDirectory(rootPath)) {
      val iterator = fs.listFiles(new Path(rootDir), false)
      val list = new ListBuffer[FileStatus]
      while (iterator.hasNext) {
        list += iterator.next()
      }
      list.par.map(p => {
        val path = p.getPath
        val fileStat = fs.getFileStatus(path)
        DsFile(path.getName, new Timestamp(fileStat.getModificationTime), fileStat.getLen)
      }).seq
    } else if (rootDir.endsWith(".csv") && (fs.exists(rootPath) || csvFile.exists())) {
      Seq(DsFile(csvFile.getName, new Timestamp(csvFile.lastModified()), csvFile.length()))
    } else throw new FileNotFoundException(s"$rootDir does not exist")
  }

  def getFileDownloadInfo(filePath: String): (Long, InputStream) = {
    val path = new Path(filePath)
    if (fs.exists(path)) {
      (fs.getContentSummary(path).getLength, fs.open(path))
    } else {
      val csvFile = new File(filePath)
      (csvFile.length(), new FileInputStream(csvFile))
    }

  }

  def listLocalDirsRecursive(root: File, filterFunc : Function[File, Boolean] = null): Seq[File] = {
    val list = if (filterFunc != null) root.listFiles().filter(filterFunc) else root.listFiles()
    list.flatMap(f => if (f.isDirectory) listLocalDirsRecursive(f) else Seq(f))
      .toSeq
  }

  def listLocalDirs(rootDir: String): Seq[HFile] = {

    val list = new ListBuffer[HFile]
    val root = new File(rootDir)
    if (root.isFile) {
      val parentPath = root.getParent
      val tableName = generateUniqueTableName(rootDir, parentPath)
      list += HFile(root.getAbsolutePath, root.length(), tableName)
    } else {
      val successFiles = listLocalDirsRecursive(root, f => f.isDirectory || f.getName.endsWith("_SUCCESS"))
      if (successFiles.nonEmpty) {
        successFiles.map(sf => {
          val parentPath = sf.getParent
          val tableName = generateUniqueTableName(parentPath, rootDir)
          list += HFile(parentPath, sf.getParent.length, tableName)
        })
      } else {
        listLocalDirsRecursive(root).map(f => {
          val filePath = f.getPath
          val tableName = generateUniqueTableName(filePath, rootDir)
          list += HFile(filePath, f.length(), tableName)
        })
      }
    }
    list
  }

  def cacheKeyForParquet(rootPath: String): String = "parquetDir_" + E2EConfigUtil.trimProtocol(rootPath)

  def listHdfsDirs(rootDir: String): Map[String, HFile] = {

    val isWindows = System.getProperty("os.name").toLowerCase.contains("win")
    try {
      val list = new ListBuffer[HFile]
      val rootDirPath = new Path(rootDir)
      if (isWindows) { //TODO: Use this for local spark runs in Linux/Mac machines
        listLocalDirs(rootDir).foreach(list += _)
      }
      else if (fs.isFile(rootDirPath)) {
        val parentPath = rootDirPath.getParent
        val tableName = generateUniqueTableName(rootDir, parentPath.toString)
        list += HFile(rootDirPath.toString, fs.getContentSummary(rootDirPath).getLength, tableName)
      } else {
        //the second boolean parameter here sets the recursion to true
        val fileStatusListIterator = fs.listFiles(rootDirPath, true)
        val tmpList = new ListBuffer[FileStatus]
        var successFileFound = false
        while (fileStatusListIterator.hasNext) {
          val fileStatus = fileStatusListIterator.next
          tmpList += fileStatus
          val path = fileStatus.getPath.toString
          if (path.endsWith("/_SUCCESS")) {
            successFileFound = true
            val parentPath = fileStatus.getPath.getParent
            val tableName = generateUniqueTableName(parentPath.toString, rootDir)
            list += HFile(parentPath.toString, fs.getContentSummary(parentPath).getLength, tableName)
          }
        }
        if (!successFileFound) {
          tmpList.map(f => {
            val filePath = f.getPath
            val tableName = generateUniqueTableName(filePath.toString, rootDir)
            list += HFile(filePath.toString, fs.getContentSummary(filePath).getLength, tableName)
          })
        }

        // Pre-load the files, register each as a new table in the database.
        new Thread{
          override def run(): Unit = {
            list.par.foreach(loadFile(_))
            val dirKey = cacheKeyForParquet(rootDir)
            cache.putParquetDir(dirKey, list.map(f => f.table -> f).toMap)
            val map = list.par.map(e => e.path -> listFiles(e.path)).seq
            map.foreach(x => cache.files.get().dirs.put(E2EConfigUtil.toS3(x._1), x._2))
            cache.files.save()

            initDsFilesTable()
          }
        }.start()
      }
      list.map(x => x.table -> x).toMap
    } catch {
      case e: Exception =>
        log.error(e.getMessage, e)
        throw new IllegalStateException(e.getMessage, e)
    }
  }

  def readCapexDir(capexDir: String): immutable.Map[String, Any] = {
    e2eVisualizer.generateVisualization(capexDir)
  }

  def stopSpark(): Boolean = {
    if (!mySparkSession.sparkContext.isStopped)
      mySparkSession.stop()
    true
  }

  def restartSpark(): Boolean = {
    if (!mySparkSession.sparkContext.isStopped)
      mySparkSession.stop()
    mySparkSession = sparkConfig.spark
    SparkSession.setDefaultSession(mySparkSession)
    true
  }

  def getSparkConfigs: Map[String, String] = {
    if (mySparkSession != null && !mySparkSession.sparkContext.isStopped)
      mySparkSession.conf.getAll
    else Map.empty[String, String]
  }
}