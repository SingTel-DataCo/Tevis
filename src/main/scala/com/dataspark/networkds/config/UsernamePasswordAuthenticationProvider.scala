package com.dataspark.networkds.config

import com.dataspark.networkds.service.CacheService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider
import org.springframework.security.core.userdetails.{User, UserDetails, UsernameNotFoundException}
import org.springframework.security.authentication.{BadCredentialsException, UsernamePasswordAuthenticationToken}
import org.springframework.security.core.Authentication

import javax.security.auth.login.AccountLockedException

//@Component
class UsernamePasswordAuthenticationProvider extends AbstractUserDetailsAuthenticationProvider {

  @Autowired
  private var cache: CacheService = _

  override def setHideUserNotFoundExceptions(hideUserNotFoundExceptions: Boolean): Unit =
    super.setHideUserNotFoundExceptions(false)

  /**
   * Processes login information and attempts to authenticate the user.
   */
  override def authenticate(authentication: Authentication): Authentication = {

    val userInfo = cache.users.get().users(authentication.getPrincipal.toString)
    if (userInfo == null)
      throw new UsernameNotFoundException("Invalid username.")
    if (userInfo.password != authentication.getCredentials.toString)
      throw new BadCredentialsException("Invalid password.")
    if (userInfo.isDisabled)
      throw new AccountLockedException("Account has been disabled.")
    val user = User.withUsername(userInfo.username)
      .password(userInfo.password).roles(userInfo.roles: _*).build()
    new UsernamePasswordAuthenticationToken(user.getUsername, user.getPassword, user.getAuthorities)
  }

  /**
   * Indicates that authentication using a username and password is
   * supported.
   */
  override def supports(authentication: Class[_]) = authentication != null && authentication == classOf[UsernamePasswordAuthenticationToken]

  override def retrieveUser(s: String, usernamePasswordAuthenticationToken: UsernamePasswordAuthenticationToken): UserDetails = {
    User.withUsername(usernamePasswordAuthenticationToken.getPrincipal.toString)
      .password(usernamePasswordAuthenticationToken.getCredentials.toString)
      .build()
  }

  override def additionalAuthenticationChecks(userDetails: UserDetails, usernamePasswordAuthenticationToken: UsernamePasswordAuthenticationToken): Unit = ???
}