package com.dataspark.networkds.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.userdetails.{User, UserDetails, UserDetailsService, UsernameNotFoundException}
import org.springframework.stereotype.Service

@Service
class DbUserDetailsService extends UserDetailsService {

  @Autowired
  private var cache: CacheService = _

  override def loadUserByUsername(username: String): UserDetails = {

    val jsonDb = cache.users.get()
    if (!jsonDb.users.contains(username)) {
      throw new UsernameNotFoundException("Invalid username.")
    }
    val userInfo = jsonDb.users(username)
    User.withUsername(userInfo.username)
      .password(userInfo.password).roles(userInfo.roles: _*)
      .disabled(userInfo.isDisabled).build
  }
}
