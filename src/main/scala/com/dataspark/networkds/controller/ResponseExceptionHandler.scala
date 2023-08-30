package com.dataspark.networkds.controller

import lombok.extern.slf4j.Slf4j
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.http.{HttpHeaders, HttpStatus, ResponseEntity}
import org.springframework.web.bind.annotation.{ControllerAdvice, ExceptionHandler}
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

import javax.servlet.http.HttpServletRequest

@ControllerAdvice
@Slf4j
class ResponseExceptionHandler extends ResponseEntityExceptionHandler {

  private val log = org.slf4j.LoggerFactory.getLogger(classOf[ResponseExceptionHandler])

  @ExceptionHandler(Array(classOf[Exception])) protected def handleGeneralError(ex: Exception, request: WebRequest): ResponseEntity[AnyRef] = {
    val finalMsg = ExceptionUtils.getRootCauseMessage(ex)
    log.error(ex.getMessage, ex)
    handleExceptionInternal(ex, finalMsg, new HttpHeaders, HttpStatus.INTERNAL_SERVER_ERROR, request)
  }
}