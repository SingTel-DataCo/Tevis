package com.dataspark.networkds.controller

import com.dataspark.networkds.service.{AppService, CacheService, DbUserDetailsService, ParquetService}
import lombok.extern.slf4j.Slf4j
import org.apache.log4j.LogManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation._
import org.springframework.web.servlet.ModelAndView

import java.security.Principal

@Slf4j
@CrossOrigin(origins = Array("*"), allowedHeaders = Array("*"))
@RequestMapping(path = Array("/settings"))
@RestController
class SettingsController {

  @Autowired
  private var cache: CacheService = _

  @Autowired
  private var appService: AppService = _

  @Autowired
  private var parquetService: ParquetService = _

  @Autowired
  private var userService: DbUserDetailsService = _

  val log = LogManager.getLogger(this.getClass.getSimpleName)

  @GetMapping(path = Array("", "/", "/index"))
  def index(user: Principal): ModelAndView = {
    val mav: ModelAndView = new ModelAndView("settings")
    appService.addCommonPageObjects(mav, user, cache, parquetService)
    mav.addObject("sparkUiWebUrl",
      if (parquetService.isSparkAlive) parquetService.mySparkSession.sparkContext.uiWebUrl.get else "")
    mav
  }

  @PostMapping(path = Array("/updatePassword"))
  def modifyPassword(oldPassword: String, newPassword: String, user: Principal): Boolean = {
    userService.modifyPassword(user.getName, oldPassword, newPassword)
  }

  @PostMapping(path = Array("/updateSettings"))
  def modifyUserSettings(colorMode: String, user: Principal): Boolean = {
    val jsonDb = cache.users
    val dbUser = jsonDb.get().users(user.getName)
    val modifiedUser = dbUser.copy(colorMode = colorMode)
    jsonDb.get().users.put(dbUser.username, modifiedUser)
    jsonDb.save()
  }

}