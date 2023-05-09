package com.dataspark.networkds.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.configuration.{EnableWebSecurity, WebSecurityConfigurerAdapter}
import org.springframework.security.core.userdetails.{User, UserDetailsService}
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
class WebSecurityConfig extends WebSecurityConfigurerAdapter {

  import org.springframework.context.annotation.Bean
  import org.springframework.security.config.annotation.web.builders.HttpSecurity

  //@Bean
  //def authenticationManager(http: HttpSecurity): AuthenticationManager = http.getSharedObject(classOf[AuthenticationManagerBuilder]).build

  @Autowired
  var userService: UserDetailsService = _

  override def configure(auth: AuthenticationManagerBuilder): Unit = {
    auth.authenticationProvider(authProvider)
  }

  override def configure(http: HttpSecurity) = {
    http.authorizeRequests.antMatchers("/swagger-ui.html", "/data/*.json", "/assets/**", "/configuration/**", "/swagger-resources", "/v2/**").permitAll
    http.formLogin.loginPage("/login")
      .failureUrl("/login?error=true").permitAll()
    http.logout.permitAll
    http.authorizeRequests.anyRequest.authenticated
    http.csrf.disable
    //http.authenticationProvider(new UsernamePasswordAuthenticationProvider())
    http.headers.frameOptions.disable
    http.httpBasic
  }

  @Bean
  def authProvider: AuthenticationProvider = {
    val provider = new DaoAuthenticationProvider()
    provider.setPasswordEncoder(passwordEncoder)
    provider.setUserDetailsService(this.userService)
    provider
  }

  @Bean
  def passwordEncoder = new BCryptPasswordEncoder(10)

}
