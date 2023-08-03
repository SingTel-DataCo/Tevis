package com.dataspark.networkds.controller

import com.dataspark.networkds.beans.UserInfo
import com.dataspark.networkds.service.{AppService, CacheService, FileService, ParquetService}
import com.dataspark.networkds.util.E2EVariables
import lombok.extern.slf4j.Slf4j
import org.apache.log4j.LogManager
import org.springframework.beans.factory.annotation.{Autowired, Value}
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation._
import org.springframework.web.servlet.ModelAndView

import java.security.Principal
import java.sql.Timestamp
import scala.collection.JavaConversions.mapAsScalaMap

@Slf4j
@CrossOrigin(origins = Array("*"), allowedHeaders = Array("*"))
@RequestMapping(path = Array("/admin"))
@PreAuthorize("hasRole('ADMIN')")
@RestController
class AdminController {

  @Autowired
  private var cache: CacheService = _

  @Autowired
  private var parquetService: ParquetService = _

  @Autowired
  private var fileService: FileService = _

  @Autowired
  private var appService: AppService = _

  @Autowired
  var passwordEncoder: PasswordEncoder = _

  val log = LogManager.getLogger(this.getClass.getSimpleName)

  @GetMapping(path = Array("", "/", "/index"))
  def index(user: Principal): ModelAndView = {
    val mav: ModelAndView = new ModelAndView("admin")
    mav.addObject("version", appService.buildVersion)
    mav.addObject("capexPageEnabled", appService.capexPageEnabled)
    mav.addObject("user", user)
    mav
  }

  @GetMapping(path = Array("/getUsers"), produces = Array("application/json"))
  def getUsers: String = {
    val results = Map("data" -> cache.users.get().users.map(x => {
      val u = x._2
      Seq(u.username, u.roles.mkString(", "),
        new Timestamp(u.lastCreated.getTime).toLocalDateTime.toString,
        new Timestamp(u.lastLogin.getTime).toLocalDateTime.toString)
    }))
    E2EVariables.objectMapper.writeValueAsString(results)
  }

  @PostMapping(path = Array("/createUser"))
  def createUser(username: String, password: String, roles: String, user: Principal): Boolean = {
    val jsonDb = cache.users
    if (jsonDb.get().users.contains(username)) {
      throw new IllegalArgumentException(s"Username $username already exists. Please use another one.")
    }
    val user = UserInfo(username, passwordEncoder.encode(password), roles.split(","))
    jsonDb.get().users.put(user.username, user)
    jsonDb.save()
  }

  @PostMapping(path = Array("/modifyUser"))
  def modifyUser(username: String, password: String, roles: String, user: Principal): Boolean = {
    val jsonDb = cache.users
    if (!jsonDb.get().users.contains(username)) {
      throw new IllegalArgumentException(s"Username $username doesn't exist.")
    }
    val user = jsonDb.get().users(username)
    val pass = if (password.isEmpty) user.password else passwordEncoder.encode(password)
    val modifiedUser = UserInfo(username, pass, roles.split(","), user.isDisabled, user.lastCreated, user.lastLogin)
    jsonDb.get().users.put(username, modifiedUser)
    jsonDb.save()
  }

  @PostMapping(path = Array("/deleteUser"))
  def deleteUser(username: String): Boolean = {
    val jsonDb = cache.users
    if (!jsonDb.get().users.contains(username)) {
      throw new IllegalArgumentException(s"Username $username doesn't exist.")
    }
    jsonDb.get().users.remove(username)
    jsonDb.save()
  }

  @PostMapping(path = Array("/stopSpark"))
  def stopSpark(user: Principal): Boolean = {
    parquetService.stopSpark()
  }

  @PostMapping(path = Array("/restartSpark"))
  def restartSpark(user: Principal): Boolean = {
    parquetService.restartSpark()
  }

  @PostMapping(path = Array("/purgePastQueries"))
  def purgePastQueries(user: Principal): Int = {
    val excludeFilesFromPurging = cache.users.get().users.keys.map(cache.getUserData(_).get())
      .flatMap(ud => ud.workbook.tabs.flatMap(t => t._2.sections
        .filterNot(_._2.queryId == null).map(s => s._2.queryId + ".json"))).toSeq
    fileService.deleteQueryFilesExcept(excludeFilesFromPurging)
  }


  @GetMapping(path = Array("/getAppEnv"), produces = Array("application/json"))
  def getAppEnv: String = {
    val results = Map("data" -> (parquetService.getSparkConfigs().map(v => Seq(v._1, "SparkConfig", v._2)) ++
      System.getenv().map(v => Seq(v._1, "SystemEnv", v._2)) ++
      System.getProperties.map(v => Seq(v._1, "AppProperty", v._2)))
    )
    E2EVariables.objectMapper.writeValueAsString(results)
  }
}