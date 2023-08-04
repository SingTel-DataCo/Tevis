package com.dataspark.networkds.controller

import com.dataspark.networkds.service.AppService
import com.dataspark.networkds.util.E2EVariables
import org.apache.log4j.LogManager
import org.springframework.beans.factory.annotation.{Autowired, Value}
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation._
import org.springframework.web.servlet.ModelAndView
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder

import javax.servlet.http.{HttpServletRequest, HttpServletResponse, HttpSession}
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.AuthenticationException

import javax.annotation.Resource
import javax.validation.Valid

@CrossOrigin(origins = Array("*"), allowedHeaders = Array("*"))
@RequestMapping(path = Array("/login"))
@RestController
class LoginController {

  val log = LogManager.getLogger(this.getClass.getSimpleName)

  @Autowired
  private var appService: AppService = _

  @RequestMapping(path = Array(""))
  def index(@RequestParam(value = "error", defaultValue = "false") error: String, req: HttpServletRequest): ModelAndView = {
    val model = new ModelAndView("login")
    if (error == "true" && req.getSession.getAttribute("SPRING_SECURITY_LAST_EXCEPTION") != null) {
      val ex = req.getSession.getAttribute("SPRING_SECURITY_LAST_EXCEPTION").asInstanceOf[Exception]
      model.addObject("error", ex.getMessage)
    }
    model.addObject("admin_email", appService.adminEmail)
    model
  }
}