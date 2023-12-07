package com.dataspark.networkds.config

import com.dataspark.networkds.service.CacheService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.EventListener
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.stereotype.Component

import java.sql.Timestamp

@Component
class AuthenticationEvents {

  @Autowired
  private var cache: CacheService = _

  @EventListener
  def onSuccess(success: AuthenticationSuccessEvent): Unit = {
    val jsonDb = cache.users
    val user = jsonDb.get().users(success.getAuthentication.getName)
    val loggedInUser = user.copy(lastLogin = new Timestamp(System.currentTimeMillis()))
    jsonDb.get().users.put(loggedInUser.username, loggedInUser)
    jsonDb.save()
  }
}
