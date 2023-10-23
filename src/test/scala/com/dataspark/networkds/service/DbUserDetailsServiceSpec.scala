package com.dataspark.networkds.service

import com.dataspark.networkds.beans.{UserInfo, Users}
import com.dataspark.networkds.dao.JsonDb
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import org.mockito.Mockito._
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.test.util.ReflectionTestUtils

import scala.collection.mutable

class DbUserDetailsServiceSpec extends WordSpec with Matchers with BeforeAndAfter {

  // Mocking the CacheService
  val cacheService: CacheService = mock(classOf[CacheService])

  // Creating an instance of the DbUserDetailsService
  val dbUserDetailsService = new DbUserDetailsService
  val validUsername = "testuser"

  before {
    ReflectionTestUtils.setField(dbUserDetailsService, "cache", cacheService)
  }

  def getUsersJsonDb(): Users = {
    val validUserInfo = UserInfo(username = validUsername, password = "password", roles = Seq("USER"), isDisabled = false)
    Users(users = mutable.Map(validUsername -> validUserInfo))
  }

  "loadUserByUsername" should {
    "return UserDetails for a valid username" in {
      // Define your test data
      val jsonDb = mock(classOf[JsonDb[Users]])

      // Stubbing the cache to return the jsonDb
      when(jsonDb.get()).thenReturn(getUsersJsonDb())
      when(cacheService.users).thenReturn(jsonDb)

      // Call the method
      val userDetails = dbUserDetailsService.loadUserByUsername(validUsername)

      // Assert the result
      userDetails.getUsername should be(validUsername)
      userDetails.getAuthorities.size should be(1) // Assuming only one role
      userDetails.getAuthorities.iterator.next.getAuthority should be("ROLE_USER")
      userDetails.isEnabled should be(true)
    }

    "throw UsernameNotFoundException for an invalid username" in {
      // Define your test data
      val invalidUsername = "nonexistentuser"
      val jsonDb = mock(classOf[JsonDb[Users]])

      // Stubbing the cache to return the jsonDb
      when(jsonDb.get()).thenReturn(getUsersJsonDb())
      when(cacheService.users).thenReturn(jsonDb)

      // Call the method and expect an exception
      intercept[UsernameNotFoundException] {
        dbUserDetailsService.loadUserByUsername(invalidUsername)
      }
    }
  }
}
