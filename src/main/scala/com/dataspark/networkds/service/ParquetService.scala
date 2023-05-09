package com.dataspark.networkds.service

import com.dataspark.networkds.beans.{HFile, HFileData}
import com.dataspark.networkds.config.SparkConfig
import com.dataspark.networkds.util.{E2EConfigUtil, E2EPipelineVisualizer, SparkHadoopUtil}
import org.apache.hadoop.fs.{FileStatus, FileSystem, Path}
import org.apache.log4j.LogManager
import org.apache.spark.SparkException
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.springframework.beans.factory.annotation.{Autowired, Value}
import org.springframework.stereotype.Service

import java.io.{File, FileInputStream, FileNotFoundException, InputStream}
import java.sql.{SQLException, Timestamp}
import scala.collection.immutable
import scala.collection.immutable.ListMap
import scala.collection.mutable.ListBuffer

@Service
class ParquetService {

  val log = LogManager.getLogger(this.getClass.getSimpleName)

  @Autowired
  private var cache: CacheService = _

  @Value("${data.root.path}")
  var dataRootPath: String = _

  @Value("${capex.root.path}")
  var capexRootPath: String = _

  @Value("${query.row.limit}")
  var rowLimit: Int = _

  @Value("${unsafe_array_data.resolve}")
  var resolveUnsafeArrayData: Boolean = _

  @Autowired
  var sparkConfig: SparkConfig = _

  var mySparkSession: SparkSession = null
  def spark: SparkSession = {
    if (mySparkSession == null || mySparkSession.sparkContext.isStopped) {
      mySparkSession = sparkConfig.spark
      SparkSession.setDefaultSession(mySparkSession)
    }
    mySparkSession
  }

  def fs = {
    FileSystem.get(SparkHadoopUtil.newConfiguration(spark.sparkContext.getConf))
  }

  def e2eVisualizer = {
    new E2EPipelineVisualizer(spark, resolveUnsafeArrayData)
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

  def readCsvFile(hfile: HFile): DataFrame = {
    val tmpDf = spark.read.options(Map("inferSchema" -> "true", "header" -> "true")).csv(hfile.path)
    hfile.format = "csv"
    tmpDf
  }

  def getSchema(df: DataFrame): ListMap[String, Any] = {
    ListMap(df.schema.map(x =>
      (x.name, Map("type" -> x.dataType.simpleString, "nullable" -> x.nullable))): _*)
  }

  def readSchemaAndData(hFile: HFile, refresh: Boolean = false): HFileData = {
    val df = loadFile(hFile, refresh)
    if (hFile.schema == null) hFile.schema = getSchema(df)
    val sql = s"SELECT * FROM ${hFile.table} LIMIT $rowLimit"
    HFileData(data = e2eVisualizer.extractDataInJson(df, rowLimit), format = hFile.format,
      path = hFile.table, schema = hFile.schema, sql = sql)
  }

  def queryTable(sql: String): HFileData = {
    validateSql(sql)
    val tokens = sql.split(";")
    var df = spark.sql(tokens(0))
    if (tokens.length > 1) {
      val command = tokens(1).trim.toLowerCase()
      df = if (command == "summary") df.summary() else if (command == "describe") df.describe() else df
    }
    val customLimit = "(?i)LIMIT\\s+(\\d+)".r.findAllMatchIn(sql).toSeq
    val limit = if (customLimit.isEmpty) rowLimit else customLimit.last.group(1).toInt
    HFileData(data = e2eVisualizer.extractDataInJson(df, limit), format = null,
      path = null, schema = getSchema(df), sql = sql)
  }

  private def validateSql(sql: String): Unit = {
    if ("(?i)DROP\\s+TABLE".r.findAllMatchIn(sql).nonEmpty)
      throw new SQLException("DROP TABLE command is not allowed.")
    if (sql.contains(";")) {
      val postDfCommand = ";(.+)".r.findAllMatchIn(sql).toSeq
      if (postDfCommand.nonEmpty) {
        val command = postDfCommand.head.group(1).trim.toLowerCase
        if (!Seq("summary", "describe").contains(command))
           throw new SQLException(s"$command: invalid Spark DataFrame function.")
      }
    }
  }

  /**
   * Converts a row to a Map. The row could be a Map already, or an instance of GenericRowWithSchema.
   *
   * @param row
   * @return
   */
  def toMap(row: Any): Map[String, Any] = {
    if (row.isInstanceOf[Map[String, Any]]) row.asInstanceOf[Map[String, Any]] else {
      val rowWithSchema = row.asInstanceOf[GenericRowWithSchema]
      rowWithSchema.schema.map(_.name).map(x => x -> rowWithSchema.getAs(x)).toMap
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

  def generateTableName(dsPath: String, rootPath: String): String = {

    val dsPath2 = E2EConfigUtil.trimProtocol(dsPath)
    val rootPath2 = E2EConfigUtil.trimProtocol(rootPath)

    dsPath2.substring(dsPath2.indexOf(rootPath2) + rootPath2.length)
      .replace("sub_module=", "").replace("module=", "")
      .replaceAll("\\/|=|\\+|-|\\.|<|>|\\*", " ")
      .trim.replaceAll("\\s+", "_")
  }

  def listFiles(rootDir: String): Seq[Seq[Any]] = {
    val rootPath = new Path(rootDir)
    val csvFile = new File(rootDir) //must be a local CSV file, just in case Hadoop path doesn't exist

    if (fs.exists(rootPath)) {
      val iterator = fs.listFiles(new Path(rootDir), false)
      val list = new ListBuffer[FileStatus]
      while (iterator.hasNext) {
        list += iterator.next()
      }
      list.par.map(p => {
        val path = p.getPath
        val fileStat = fs.getFileStatus(path)
        Seq(path.getName, new Timestamp(fileStat.getModificationTime).toLocalDateTime.toString, fileStat.getLen, path.toString)
      }).seq
    } else if (rootDir.endsWith(".csv") && csvFile.exists()) {
      Seq(Seq(csvFile.getName, new Timestamp(csvFile.lastModified()).toLocalDateTime.toString, csvFile.length(), rootDir))
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

  def listHdfsDirs(rootDir: String): Map[String, HFile] = {

    try {
      val list = new ListBuffer[HFile]
      val rootDirPath = new Path(rootDir)
      if (fs.isFile(rootDirPath)) {
        val parentPath = rootDirPath.getParent
        val tableName = generateUniqueTableName(rootDir, parentPath.toString)
        list += HFile(rootDirPath.toString, fs.getContentSummary(rootDirPath).getLength, tableName)
      } else {
        //the second boolean parameter here sets the recursion to true
        val fileStatusListIterator = fs.listFiles(rootDirPath, true)
        while (fileStatusListIterator.hasNext) {
          val fileStatus = fileStatusListIterator.next
          val path = fileStatus.getPath.toString
          if (path.endsWith("/_SUCCESS")) {
            val parentPath = fileStatus.getPath.getParent
            val tableName = generateUniqueTableName(parentPath.toString, rootDir)
            list += HFile(parentPath.toString, fs.getContentSummary(parentPath).getLength, tableName)
          }
        }
        // Pre-load the files, register each as a new table in the database.
        new Thread{
          override def run(): Unit = {
            list.par.foreach(loadFile(_))
            cache.parquetDirs.save() // to save the schema
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

  def getSparkConfigs() : Map[String, String] = {
    if (mySparkSession != null && !mySparkSession.sparkContext.isStopped)
      mySparkSession.conf.getAll
    else Map.empty[String, String]
  }
}