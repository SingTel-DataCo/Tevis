package com.dataspark.networkds.service

import com.dataspark.networkds.beans.UserInfo
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

  def createUser(username: String, password: String): Boolean = {
    createUser(username, password, "USER")
  }

  def createUser(username: String, password: String, roles: String): Boolean = {
    val jsonDb = cache.users
    if (jsonDb.get().users.contains(username)) {
      throw new IllegalArgumentException(s"Username $username already exists. Please use another one.")
    }
    val user = UserInfo(username, cache.passwordEncoder.encode(password), roles.split(","))
    jsonDb.get().users.put(user.username, user)
    jsonDb.save()
  }

  def modifyUser(username: String, password: String, roles: String): Boolean = {
    val jsonDb = cache.users
    if (!jsonDb.get().users.contains(username)) {
      throw new IllegalArgumentException(s"Username $username doesn't exist.")
    }
    val user = jsonDb.get().users(username)
    val pass = if (password.isEmpty) user.password else cache.passwordEncoder.encode(password)
    val modifiedUser = UserInfo(username, pass, roles.split(","), user.isDisabled, user.lastCreated, user.lastLogin)
    jsonDb.get().users.put(username, modifiedUser)
    jsonDb.save()
  }

  def modifyPassword(username: String, oldPassword: String, newPassword: String): Boolean = {
    val jsonDb = cache.users
    val dbUser = jsonDb.get().users(username)
    if (!cache.passwordEncoder.matches(oldPassword, dbUser.password)) {
      throw new IllegalArgumentException("Old password is incorrect.")
    }
    val modifiedUser = UserInfo(dbUser.username, cache.passwordEncoder.encode(newPassword),
      dbUser.roles, dbUser.isDisabled, dbUser.lastCreated, dbUser.lastLogin)
    jsonDb.get().users.put(dbUser.username, modifiedUser)
    jsonDb.save()
  }
}
