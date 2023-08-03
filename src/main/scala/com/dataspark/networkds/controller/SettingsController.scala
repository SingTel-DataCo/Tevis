package com.dataspark.networkds.controller

import com.dataspark.networkds.beans.UserInfo
import com.dataspark.networkds.service.{AppService, CacheService, ParquetService}
import com.dataspark.networkds.util.E2EVariables
import lombok.extern.slf4j.Slf4j
import org.apache.log4j.LogManager
import org.springframework.beans.factory.annotation.{Autowired, Value}
import org.springframework.security.crypto.password.PasswordEncoder
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
  var passwordEncoder: PasswordEncoder = _

  @Autowired
  private var appService: AppService = _

  val log = LogManager.getLogger(this.getClass.getSimpleName)

  @GetMapping(path = Array("", "/", "/index"))
  def index(user: Principal): ModelAndView = {
    val mav: ModelAndView = new ModelAndView("settings")
    mav.addObject("version", appService.buildVersion)
    mav.addObject("capexPageEnabled", appService.capexPageEnabled)
    mav.addObject("user", user)
    mav
  }

  @PostMapping(path = Array("/updatePassword"))
  def modifyUser(oldPassword: String, newPassword: String, user: Principal): Boolean = {
    val jsonDb = cache.users
    val dbUser = jsonDb.get().users(user.getName)
    if (!passwordEncoder.matches(oldPassword, dbUser.password)) {
      throw new IllegalArgumentException("Old password is incorrect.")
    }
    val modifiedUser = UserInfo(dbUser.username, passwordEncoder.encode(newPassword),
      dbUser.roles, dbUser.isDisabled, dbUser.lastCreated, dbUser.lastLogin)
    jsonDb.get().users.put(dbUser.username, modifiedUser)
    jsonDb.save()
  }

}