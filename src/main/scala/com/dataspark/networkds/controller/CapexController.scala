package com.dataspark.networkds.controller

import com.dataspark.networkds.service.{AppService, CacheService, FileService, ParquetService}
import com.dataspark.networkds.util.E2EVariables
import lombok.extern.slf4j.Slf4j
import org.apache.log4j.LogManager
import org.springframework.beans.factory.annotation.{Autowired, Value}
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.web.bind.annotation._
import org.springframework.web.servlet.ModelAndView

import java.security.Principal

@Slf4j
@CrossOrigin(origins = Array("*"), allowedHeaders = Array("*"))
@ConditionalOnExpression("${capex.page.enabled:false}")
@RequestMapping(path = Array("/capex"))
@RestController
class CapexController {

  @Autowired
  private var parquetService: ParquetService = _

  @Autowired
  private var cache: CacheService = _

  @Autowired
  private var appService: AppService = _

  @Autowired
  private var fileService: FileService = _

  val log = LogManager.getLogger(this.getClass.getSimpleName)

  @GetMapping(path = Array("", "/", "/index"))
  def index(user: Principal): ModelAndView = {
    val userData = cache.getUserData(user.getName)
    if (userData.get().capexDir == null) {
      userData.get().capexDir = appService.capexRootPath
      userData.get().capexDirHistory.add(appService.capexRootPath)
      userData.save()
    }
    val mav: ModelAndView = new ModelAndView("capex_browser")
    mav.addObject("version", appService.buildVersion)
    mav.addObject("user", user)
    mav.addObject("capexDir", userData.get().capexDir)
    mav.addObject("jsEventsMinimize", appService.jsEventsMinimize)
    val dbUser = cache.users.get().users(user.getName)
    mav.addObject("darkMode", dbUser.darkMode)
    mav
  }

  @GetMapping(path = Array("/browseDir"), produces = Array("application/json"))
  @ResponseBody
  def readCapexDir(@RequestParam dir: String, user: Principal): String = {
    log.info(dir)
    // Reading CAPEX directories only takes less than 5 seconds, so put a 1min timeout on caching
    val capexDirInfo = cache.getCapexDirOrElseUpdate("capexDir_" + dir, () => parquetService.readCapexDir(dir))
      .asInstanceOf[Map[String, Any]]

    val userData = cache.getUserData(user.getName)
    userData.get().capexDir = dir
    userData.get().capexDirHistory.add(dir)
    userData.save()
    fileService.writeCapexDirAsync(dir, capexDirInfo)
    E2EVariables.objectMapper.writeValueAsString(capexDirInfo)
  }

  @GetMapping(path = Array("/capexDirHistory"), produces = Array("application/json"))
  def getCapexDirHistory(user: Principal): String = {
    val userData = cache.getUserData(user.getName)
    E2EVariables.objectMapper.writeValueAsString(userData.get().capexDirHistory)
  }

}