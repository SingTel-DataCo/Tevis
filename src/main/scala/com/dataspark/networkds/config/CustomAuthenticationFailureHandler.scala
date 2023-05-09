package com.dataspark.networkds.config

import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

class CustomAuthenticationFailureHandler extends AuthenticationFailureHandler {
  override def onAuthenticationFailure(request: HttpServletRequest, response: HttpServletResponse, e: AuthenticationException): Unit =
    response.sendRedirect("/login?error=" + e.getMessage)
}
