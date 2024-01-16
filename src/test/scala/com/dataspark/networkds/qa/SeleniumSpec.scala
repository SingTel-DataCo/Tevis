package com.dataspark.networkds.qa

import com.dataspark.networkds.Application
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}
import org.openqa.selenium.{By, JavascriptExecutor}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

import java.time.Duration

class SeleniumSpec extends FlatSpec with BeforeAndAfterAll with Matchers {

  var driver: ChromeDriver = null
  var js: JavascriptExecutor = null
  var app: ConfigurableApplicationContext = null

  override def beforeAll(): Unit = {
    val options: ChromeOptions = new ChromeOptions()
    options.addArguments("--headless")
    driver = new ChromeDriver(options)
    js = driver.asInstanceOf[JavascriptExecutor]
    app = SpringApplication.run(classOf[Application], "--startup.no.browser.launch=true")
  }

  override def afterAll(): Unit = {
    driver.close()
    app.close()
  }

  "LoginUI" should "allow login and logout of the default user" in {

    val port = app.getEnvironment.getProperty("server.port")
    driver.get(s"http://localhost:$port/login")

    val wait = new WebDriverWait(driver, Duration.ofSeconds(1))
    wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username")))

    driver.findElement(By.id("username")).click()
    driver.findElement(By.id("username")).sendKeys("dataspark-admin")
    driver.findElement(By.id("password")).click()
    driver.findElement(By.id("password")).sendKeys("dataspark-admin")
    // Click login button
    driver.findElement(By.cssSelector("input[type=\"submit\"]")).click()
    val wait2 = new WebDriverWait(driver, Duration.ofSeconds(5))
    wait2.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".navbar")))

    driver.findElement(By.linkText("dataspark-admin")).click()
    driver.findElement(By.linkText("Logout")).click()

    val wait3 = new WebDriverWait(driver, Duration.ofSeconds(1))
    wait3.until(ExpectedConditions.visibilityOfElementLocated(By.id("username")))

  }

}
