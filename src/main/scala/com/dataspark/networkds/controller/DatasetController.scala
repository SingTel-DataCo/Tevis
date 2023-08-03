package com.dataspark.networkds.controller

import com.dataspark.networkds.beans.{HFile, HFileData, QueryTx, Section, Tab, UserData, Workbook}
import com.dataspark.networkds.service.{AppService, CacheService, FileService, ParquetService}
import com.dataspark.networkds.util.{E2EConfigUtil, E2EVariables, Str}
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import org.apache.logging.log4j.LogManager
import org.springframework.beans.factory.annotation.{Autowired, Value}
import org.springframework.core.io.{InputStreamResource, Resource}
import org.springframework.http.{MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.{CrossOrigin, GetMapping, PostMapping, RequestBody, RequestMapping, RequestParam, ResponseBody, RestController}
import org.springframework.web.servlet.ModelAndView

import java.security.Principal
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
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

  val log = LogManager.getLogger(this.getClass.getSimpleName)
  val readableTimeFormat = DateTimeFormatter.ofPattern("YYYYMMdd_HHmmss")

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

  def cacheKeyForParquet(rootPath: String): String = "parquetDir_" + E2EConfigUtil.trimProtocol(rootPath)

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
    val hFile = dirList.values.find(hf => E2EConfigUtil.trimProtocol(hf.path).endsWith(E2EConfigUtil.trimProtocol(path))).orNull
    if (hFile == null) {
      throw new SQLException(s"Dataset path '$path' doesn't exist.")
    }
    // Always refresh the table; it's just a single table anyway so it's not
    E2EVariables.objectMapper.writeValueAsString(parquetService.readSchemaAndData(hFile, true))
  }

  def processSqlResponse(user: Principal, results: HFileData): String = {
    val readableTime = LocalDateTime.now.format(readableTimeFormat)
    val queryId = s"${user.getName}_$readableTime"
    val queryTx = QueryTx(queryId, new Date(), user.getName, results)
    fileService.writeToFileAsync(queryTx)
    E2EVariables.objectMapper.writeValueAsString(queryTx)
  }

  @PostMapping(path = Array("/queryTable"), produces = Array("application/json"))
  @ResponseBody
  def queryTable(@RequestParam sql: String, user: Principal): String = {
    log.info(sql)
    val results = parquetService.queryTable(sql)
    val userData = cache.getUserData(user.getName)
    val sqlHistory = userData.get().sqlHistory
    sqlHistory.insert(0, sql)
    if (sqlHistory.length > 10)
      sqlHistory.remove(sqlHistory.length - 10)
    userData.save()
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
    val results = Map("data" -> parquetService.listFiles(path))
    E2EVariables.objectMapper.writeValueAsString(results)
  }

  @GetMapping(path = Array("/download"))
  def download(path: String, user: Principal): ResponseEntity[Resource] = {
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
    true
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

  case class TreeViewNode(text: String, path: String = null, nodes: Seq[TreeViewNode] = null, tags: Seq[Int] = null)

  def buildTreeViewData(dirKeys: Iterable[String], selectedFolder: String = ""): AnyRef = {
    val ord = Ordering.by { foo: HFile => foo.path }
    dirKeys.toSeq.sorted
      .map(d => d -> cache.getParquetDirOrElseUpdate(cacheKeyForParquet(d), () => parquetService.listHdfsDirs(d)).values)
      .map(x => {
        val nodes = x._2.toSeq.sorted(ord).map(c => TreeViewNode(c.table, c.path))
        TreeViewNode(x._1, nodes = nodes, tags = Seq(nodes.size))
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