package com.dataspark.networkds.controller

import com.dataspark.networkds.service.{AppService, DbUserDetailsService}
import org.apache.log4j.LogManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation._
import org.springframework.web.servlet.ModelAndView

import javax.servlet.http.HttpServletRequest

@CrossOrigin(origins = Array("*"), allowedHeaders = Array("*"))
@RequestMapping(path = Array(""))
@RestController
class LoginController {

  val log = LogManager.getLogger(this.getClass.getSimpleName)

  @Autowired
  private var appService: AppService = _

  @Autowired
  private var userService: DbUserDetailsService = _

  @RequestMapping(path = Array("/login"))
  def index(@RequestParam(value = "error", defaultValue = "false") error: String, req: HttpServletRequest): ModelAndView = {
    val model = new ModelAndView("login")
    if (error == "true" && req.getSession.getAttribute("SPRING_SECURITY_LAST_EXCEPTION") != null) {
      val ex = req.getSession.getAttribute("SPRING_SECURITY_LAST_EXCEPTION").asInstanceOf[Exception]
      model.addObject("error", ex.getMessage)
    }
    model.addObject("registerPageEnabled", appService.registerPageEnabled)
    model.addObject("adminEmail", appService.adminEmail)
    model
  }

  @GetMapping(path = Array("/register"))
  def register(): ModelAndView = {
    val model = if (appService.registerPageEnabled) new ModelAndView("register")
    else new ModelAndView("redirect:/login")
    model.addObject("adminEmail", appService.adminEmail)
    model
  }

  @PostMapping(path = Array("/register"))
  def createAccount(username: String, password: String): Boolean = {
    userService.createUser(username, password)
  }
}