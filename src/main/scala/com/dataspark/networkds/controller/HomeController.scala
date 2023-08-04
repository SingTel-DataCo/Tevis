package com.dataspark.networkds.controller

import lombok.extern.slf4j.Slf4j
import org.apache.log4j.LogManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation._
import org.springframework.web.servlet.ModelAndView

import java.security.Principal

@Slf4j
@CrossOrigin(origins = Array("*"), allowedHeaders = Array("*"))
@RequestMapping(path = Array("", "/"))
@RestController
class HomeController {

  val log = LogManager.getLogger(this.getClass.getSimpleName)

  @Value("${capex.page.enabled:false}")
  var capexPageEnabled: Boolean = _

  @GetMapping(path = Array("", "/", "/index"))
  def index(user: Principal): ModelAndView = {
    val redirectPage = if (capexPageEnabled) "capex" else "dataset"
    new ModelAndView("redirect:/" + redirectPage)
  }
}