package com.dataspark.networkds

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.{Bean, PropertySource}
import org.springframework.web.servlet.config.annotation.{CorsRegistry, EnableWebMvc, ResourceHandlerRegistry, WebMvcConfigurer}

@SpringBootApplication
@PropertySource(Array("classpath:application.properties"))
class Application {

  @Bean
  @EnableWebMvc
  def corsConfigurer(): WebMvcConfigurer = {
    new WebMvcConfigurer() {
      override def addCorsMappings(registry: CorsRegistry): Unit = {
        registry.addMapping("/**").allowedOrigins("*").allowedMethods("*")
      }

      override def addResourceHandlers(registry: ResourceHandlerRegistry): Unit = {
        registry.addResourceHandler("swagger-ui.html").addResourceLocations("classpath:/META-INF/resources/")
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/")
      }
    }
  }
}

object Application extends App {

  SpringApplication.run(classOf[Application])
}