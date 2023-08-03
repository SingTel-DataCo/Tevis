package com.dataspark.networkds.config

import org.apache.logging.log4j.LogManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener

import java.awt.Desktop
import java.io.IOException
import java.net.URISyntaxException
import java.net.URI

@Configuration
class StartupConfig {

  val log = LogManager.getLogger(this.getClass.getSimpleName)

  @Value("${server.port}")
  var serverPort: Int = _

  @EventListener(Array(classOf[ApplicationReadyEvent]))
  def applicationReadyEvent(): Unit = {
    val serverUrl = "http://localhost:" + serverPort
    log.info("Launching browser: " + serverUrl)
    browse(serverUrl)
  }

  // This method is copied from https://stackoverflow.com/a/60449216/3369952
  def browse(url: String): Unit = {
    if (Desktop.isDesktopSupported) {
      val desktop = Desktop.getDesktop
      try desktop.browse(new URI(url))
      catch {
        case e@(_: IOException | _: URISyntaxException) =>
          e.printStackTrace()
      }
    }
    else {
      val runtime = Runtime.getRuntime
      var command: Array[String] = null

      val operatingSystemName = System.getProperty("os.name").toLowerCase
      if (operatingSystemName.contains("nix") || operatingSystemName.contains("nux")) {
        val browsers = Seq("opera", "google-chrome", "epiphany", "firefox", "mozilla", "konqueror", "netscape", "links", "lynx")
        val stringBuffer = new StringBuffer
        for (i <- 0 until browsers.length) {
          if (i == 0) stringBuffer.append(String.format("%s \"%s\"", browsers(i), url))
          else stringBuffer.append(String.format(" || %s \"%s\"", browsers(i), url))
        }
        command = Array("sh", "-c", stringBuffer.toString)
      }
      else if (operatingSystemName.contains("win")) command = Array("rundll32 url.dll,FileProtocolHandler " + url)
      else if (operatingSystemName.contains("mac")) command = Array("open " + url)
      else {
        System.out.println("an unknown operating system!!")
        return
      }

      try if (command.length > 1) runtime.exec(command) // linux
      else runtime.exec(command(0)) // windows or mac
      catch {
        case e: IOException =>
          e.printStackTrace()
      }
    }
  }
}