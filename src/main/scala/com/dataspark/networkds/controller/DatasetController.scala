package com.dataspark.networkds.controller

import com.dataspark.networkds.beans.{HFile, HFileData, QueryTx, Section, Tab, Workbook}
import com.dataspark.networkds.service.{AppService, CacheService, FileService, ParquetService}
import com.dataspark.networkds.util.{E2EConfigUtil, E2EVariables, Str}
import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.{InputStreamResource, Resource}
import org.springframework.http.{MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.{CrossOrigin, GetMapping, PostMapping, RequestMapping, RequestParam, ResponseBody, RestController}
import org.springframework.web.servlet.ModelAndView

import java.io.File
import java.security.Principal
import java.sql.SQLException
import java.time.{LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter
import java.sql.Timestamp
import javax.servlet.http.HttpServletResponse
import scala.util.Random

@CrossOrigin(origins = Array("*"), allowedHeaders = Array("*"))
@RequestMapping(path = Array("/dataset"))
@RestController
class DatasetController {

  @Autowired
  private var parquetService: ParquetService = _

  @Autowired
  private var cache: CacheService = _

  @Autowired
  private var fileService: FileService = _

  @Autowired
  private var appService: AppService = _

  val log = LoggerFactory.getLogger(this.getClass.getSimpleName)

  @GetMapping(path = Array("", "/", "/index"))
  def index(user: Principal): ModelAndView = {
    val userData = cache.getUserData(user.getName)
    if (userData.get().parquetDir == null) {
      userData.get().parquetDir = appService.dataRootPath
      userData.save()
    }
    val mav: ModelAndView = new ModelAndView("dataset_browser")
    mav.addObject("version", appService.buildVersion)
    mav.addObject("user", user)
    mav.addObject("dataRootDir", userData.get().parquetDir)
    mav.addObject("jsEventsMinimize", appService.jsEventsMinimize)
    mav.addObject("capexPageEnabled", appService.capexPageEnabled)
    val dbUser = cache.users.get().users(user.getName)
    mav.addObject("colorMode", dbUser.colorMode)
    mav
  }

  @GetMapping(path = Array("/share"))
  def shareLink(@RequestParam("sid") shareId: String, user: Principal, response: HttpServletResponse): ModelAndView = {
    if (shareId != null && shareId.nonEmpty) {
      try {
        copySharedTabOrSection(shareId, user.getName)
      } catch {
        case ex: Exception =>
          log.error(ex.getMessage, ex)
          val mav: ModelAndView = new ModelAndView("error")
          mav.addObject("message", ex.getMessage)
          mav
      }
    }
    response.sendRedirect("/dataset")
    null
  }

  def cacheKeyForParquet(rootPath: String): String = parquetService.cacheKeyForParquet(rootPath)

  @GetMapping(path = Array("/getDataFromTable"), produces = Array("application/json"))
  @ResponseBody
  def getDataFromTable(@RequestParam table: String, @RequestParam rootPath: String, user: Principal): String = {
    val hFile = cache.getParquetDir(cacheKeyForParquet(rootPath))(table)
    val results = parquetService.readSchemaAndData(hFile)
    processSqlResponse(user, results)
  }

  @GetMapping(path = Array("/getDataFromPath"), produces = Array("application/json"))
  @ResponseBody
  def getDataFromPath(@RequestParam path: String, @RequestParam rootDir: String,
                      user: Principal): String = {
    val dirList = cache.getParquetDirOrElseUpdate(cacheKeyForParquet(rootDir),
      () => parquetService.listHdfsDirs(rootDir))
    val userData = cache.getUserData(user.getName)
    userData.get().datasets.add(rootDir)
    userData.save()
    var hFile = dirList.values.find(hf => E2EConfigUtil.trimProtocol(hf.path).endsWith(E2EConfigUtil.trimProtocol(path))).orNull
    if (hFile == null) {
      val f = new File(path)
      if (f.exists() && path.endsWith(".csv")) {
        hFile = parquetService.readCsvFileLocal(f)
        cache.putParquetDir(cacheKeyForParquet(rootDir), dirList ++ Map(hFile.table -> hFile))
      } else {
        throw new SQLException(s"Dataset path '$path' doesn't exist.")
      }
    }
    E2EVariables.objectMapper.writeValueAsString(parquetService.readSchemaAndData(hFile))
  }

  def processSqlResponse(user: Principal, results: HFileData): String = {
    val readableTime = LocalDateTime.now.format(parquetService.readableTimeFormat)
    val queryId = s"${user.getName}_$readableTime"
    val queryTx = QueryTx(queryId, new Timestamp(System.currentTimeMillis()), user.getName, results)
    fileService.writeQueryTxAsync(queryTx)
    E2EVariables.objectMapper.writeValueAsString(queryTx)
  }

  def saveToHistory(sql: String, user: Principal): Unit = {
    val userData = cache.getUserData(user.getName)
    val sqlHistory = userData.get().sqlHistory
    sqlHistory.insert(0, sql)
    if (sqlHistory.length > 10)
      sqlHistory.remove(sqlHistory.length - 10)
    userData.save()
  }

  @PostMapping(path = Array("/queryTable"), produces = Array("application/json"))
  @ResponseBody
  def queryTable(@RequestParam sql: String, user: Principal): String = {
    log.info(sql)
    val results = parquetService.queryAndGetJsonWithRetry(sql)
    saveToHistory(sql, user)
    processSqlResponse(user, results)
  }

  @GetMapping(path = Array("/getCachedQuery"), produces = Array("application/json"))
  @ResponseBody
  def getCachedQuery(@RequestParam queryId: String): String = {
    val queryTx = fileService.readQueryTx(queryId)
    if (queryTx.nonEmpty) {
      E2EVariables.objectMapper.writeValueAsString(queryTx.get.query)
    } else null
  }

  @GetMapping(path = Array("/list"), produces = Array("application/json"))
  @ResponseBody
  def listAllDirs(path: String, refresh: Boolean = false, user: Principal): String = {
    val pathR = FilenameUtils.separatorsToUnix(path)
    log.info(s"Listing contents of path '$pathR'")
    val userData = cache.getUserData(user.getName)
    val dirList = if (refresh) {
      val key = cacheKeyForParquet(pathR)
      var list = cache.getParquetDir(key)
      list.keys.map(parquetService.dropView)
      cache.parquetDirs.get().dirs.remove(key)
      list = parquetService.listHdfsDirs(pathR)
      cache.putParquetDir(cacheKeyForParquet(pathR), list)
      list
    }
    else if (pathR.nonEmpty) {
      val list = cache.getParquetDirOrElseUpdate(cacheKeyForParquet(pathR), () => parquetService.listHdfsDirs(pathR))
      userData.get().datasets.add(pathR)
      userData.save()
      list
    }
    E2EVariables.objectMapper.writeValueAsString(buildTreeViewData(userData.get().datasets, pathR))
  }

  @GetMapping(path = Array("/listFiles"), produces = Array("application/json"))
  @ResponseBody
  def listFiles(path: String, user: Principal): String = {
    val results = Map("data" -> cache.getDsFiles(path).map(f => {
      val dateFormatted = f.date.toInstant.atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
      val filepath = path + (if (path.endsWith(".csv")) "" else "/" + f.filename)
      Seq(f.filename, dateFormatted, f.size, filepath)
    }))
    E2EVariables.objectMapper.writeValueAsString(results)
  }

  @GetMapping(path = Array("/download"))
  def download(path: String): ResponseEntity[Resource] = {
    val (fileLength, inputStream) = parquetService.getFileDownloadInfo(path)
    val resource = new InputStreamResource(inputStream)
    ResponseEntity.ok().contentLength(fileLength)
      .contentType(MediaType.APPLICATION_OCTET_STREAM)
      .body(resource)
  }

  @GetMapping(path = Array("/refreshTable"))
  @ResponseBody
  def refreshTable(@RequestParam table: String, @RequestParam rootPath: String): Boolean = {
    val hFile = cache.getParquetDir(cacheKeyForParquet(rootPath))(table)
    parquetService.loadFile(hFile, true)
    cache.parquetDirs.save()
    cache.files.get().dirs.put(hFile.path, parquetService.listFiles(hFile.path))
    cache.files.save()
    true
  }

  @GetMapping(path = Array("/schema"), produces = Array("application/json"))
  @ResponseBody
  def schema(@RequestParam table: String, @RequestParam rootPath: String): String = {
    val hFile = cache.getParquetDir(cacheKeyForParquet(rootPath))(table)
    E2EVariables.objectMapper.writeValueAsString(hFile.schema)
  }

  @GetMapping(path = Array("/generateSql"), produces = Array("application/json"))
  @ResponseBody
  def generateSql(@RequestParam params: java.util.Map[String, String]): String = {
    val table = params.get("table")
    val `type` = params.get("type")
    val rootPath = params.get("rootPath")
    val hFile = cache.getParquetDir(cacheKeyForParquet(rootPath))(table)
    if (`type` == "SELECT_COLS") {
      val columns = hFile.schema.keys.mkString(", ")
      val sql = s"SELECT $columns FROM ${hFile.table} LIMIT ${parquetService.rowLimit}"
      E2EVariables.objectMapper.writeValueAsString(Map("sql" -> sql))
    } else if (`type` == "CACHE_TABLE") {
      val sql = s"CACHE TABLE cache1 AS (SELECT * FROM ${hFile.table})"
      E2EVariables.objectMapper.writeValueAsString(Map("sql" -> sql))
    } else if (`type` == "PIVOT") {
      val cols = params.get("cols").split(",").map(_.trim)
      val rows = params.get("rows").split(",").map(_.trim)
      val aggs = params.get("aggs").split(",").map(_.trim)
      val allCols = (cols ++ rows ++
        aggs.map(a => a.replaceAll(".*\\((.*?)\\)", "$1")))
        .filter(_.nonEmpty).distinct.mkString(", ")
      val sql = if (params.get("language") == "scala") {
        "%scala\n" +
          s"val df = spark.read.table(${'"' + table + '"'})\n" +
          s"df.select(${allCols.split(", ").map(c => '"' + c + '"').mkString(", ")})" +
          s".groupBy(${rows.map(r => '"' + r + '"').mkString(", ")})" +
          s".pivot(${'"' + cols(0) + '"'})" +
          s".agg(${aggs.map(a => a.replaceAll("(.*)\\((.*?)\\)", "$1(\"$2\").as(\"$2\")")).mkString(", ")})"
      }
      else {
        val colVals = parquetService.spark.table(table)
          .select(cols.map(org.apache.spark.sql.functions.col): _*)
          .distinct().collect().map(r => cols.indices.map(i => s"'${r.get(i)}'").mkString("(", ",", ")")).mkString(", ")
        s"SELECT * FROM (SELECT $allCols FROM $table) pivot (${params.get("aggs")} for (${params.get("cols")}) in ($colVals))"
      }
      E2EVariables.objectMapper.writeValueAsString(Map("sql" -> sql))
    } else {
      throw new IllegalArgumentException(s"Invalid type '${`type`}'")
    }
  }

  @PostMapping(path = Array("/unmount"), produces = Array("application/json"))
  @ResponseBody
  def unmountPath(@RequestParam path: String, user: Principal): String = {
    val userData = cache.getUserData(user.getName)
    userData.get().datasets.remove(path)
    userData.save()
    E2EVariables.objectMapper.writeValueAsString(buildTreeViewData(userData.get().datasets))
  }

  @PostMapping(path = Array("/syncWork"), produces = Array("application/json"))
  @ResponseBody
  def syncWork(@RequestParam workbook: String, user: Principal): String = {
    val wb: Workbook = E2EVariables.objectMapper.readValue(workbook, classOf[Workbook])
    val userData = cache.getUserData(user.getName)
    if (wb.tabs.nonEmpty) {
      userData.get().workbook = wb
      userData.save()
    }
    E2EVariables.objectMapper.writeValueAsString(userData.get().workbook)
  }

  @GetMapping(path = Array("/getShareLink"), produces = Array("application/json"))
  @ResponseBody
  def getShareLink(@RequestParam tabId: String, @RequestParam sectionId: String, user: Principal): String = {
    val shareId = Str.encodeBase64(Seq(Random.alphanumeric.take(5).mkString,
      user.getName, tabId, sectionId).mkString(":"))
    E2EVariables.objectMapper.writeValueAsString(Map("shareId" -> shareId))
  }

  case class TreeViewNode(text: String, path: String = null, format: String = null, size: String = null,
                          nodes: Seq[TreeViewNode] = null, tags: Seq[Int] = null)

  def buildTreeViewData(dirKeys: Iterable[String], selectedFolder: String = ""): AnyRef = {
    val ord = Ordering.by { foo: HFile => foo.path }
    dirKeys.toSeq.sorted
      .map(d => d -> cache.getParquetDirOrElseUpdate(cacheKeyForParquet(d), () => parquetService.listHdfsDirs(d)).values)
      .map(x => {
        val nodes = x._2.toSeq.sorted(ord).map(c => TreeViewNode(c.table, c.path, c.format, Str.formatFileSize(c.size)))
        TreeViewNode(x._1, nodes = nodes, tags = Seq(nodes.size), size = Str.formatFileSize(x._2.map(_.size).sum))
      })
  }

  def copySharedTabOrSection(shareId: String, username: String) = {
    val tokens = Str.decodeBase64(shareId).split(":")
    val sourceUser = tokens(1)
    // If you own the link you don't need to copy it
    if (sourceUser != username) {
      val tabId = tokens(2)
      val sectionId = if (tokens.length > 3) tokens(3) else null

      val sourceUserTabs = cache.getUserData(sourceUser).get().workbook.tabs
      if (sourceUserTabs.contains(tabId)) {
        val tab = sourceUserTabs(tabId)
        val currUserData = cache.getUserData(username)
        val currUserWb = currUserData.get().workbook
        val wb = if (sectionId == null) {
          copySharedTab(sourceUser, tab, currUserWb)
        } else {
          val sourceSections = tab.sections
          if (sourceSections.contains(sectionId)) {
            copySharedSection(sourceUser, sourceSections(sectionId), currUserWb)
          } else {
            throw new IllegalArgumentException("Link has expired.")
          }
        }
        currUserData.get().workbook = wb
        currUserData.save()
      } else {
        throw new IllegalArgumentException("Link has expired.")
      }
    }
  }

  def copySharedTab(sourceUser: String, tab: Tab, destWb: Workbook): Workbook = {
    val newId = destWb.tabCounter + 1
    val newTabId = "#s-tab" + newId
    val newTabContentId = "#s-tab-content" + newId
    val newSectionIdStart = destWb.sectionCounter
    val newSectionIds = (newSectionIdStart + 1 to newSectionIdStart + tab.sectionOrder.size)
      .map("#shared-section" + _).toList
    val oldNewIdMap = tab.sectionOrder.zip(newSectionIds).toMap
    val newSections = tab.sections.map(k => oldNewIdMap(k._1) -> k._2.copy(oldNewIdMap(k._1)))
    val newTab = tab.copy(tabId = newTabId, tabContentId = newTabContentId, sections = newSections, sectionOrder = newSectionIds,
      currentSection = oldNewIdMap(tab.currentSection))
    destWb.copy(currentTab = newTab.tabId, tabs = destWb.tabs ++ Map(newTab.tabId -> newTab),
      tabOrder = destWb.tabOrder ++ List(newTab.tabId), tabCounter = newId,
      sectionCounter = newSectionIdStart + tab.sectionOrder.size)
  }

  def copySharedSection(sourceUser: String, section: Section, destWb: Workbook): Workbook = {
    val tab = if (destWb.tabs.contains("#sq-tab")) destWb.tabs("#sq-tab") //shared queries tab
      else Tab("#sq-tab", "#sq-tab-content", false, "Shared", List.empty, null, Map.empty)
    val newId = destWb.sectionCounter + 1
    val newSectionId = "#shared-section" + newId
    val sectionClone = section.copy(sectionId = newSectionId)
    val newTab = tab.copy(sectionOrder = List(newSectionId) ++ tab.sectionOrder,
      currentSection = newSectionId, sections = tab.sections ++ Map(newSectionId -> sectionClone))
    destWb.copy(currentTab = newTab.tabId, tabs = destWb.tabs ++ Map(newTab.tabId -> newTab),
      tabOrder = (destWb.tabOrder ++ List(newTab.tabId)).distinct,
      sectionCounter = newId)
  }

}