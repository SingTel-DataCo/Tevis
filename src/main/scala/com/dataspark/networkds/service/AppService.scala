package com.dataspark.networkds.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.servlet.ModelAndView

import java.security.Principal

@Service
class AppService {

  @Value("${build.version}")
  var buildVersion: String = _

  @Value("${admin.email}")
  var adminEmail: String = _

  @Value("${capex.page.enabled:false}")
  var capexPageEnabled: Boolean = _

  @Value("${register.page.enabled:false}")
  var registerPageEnabled: Boolean = _

  @Value("${js_events.minimize:false}")
  var jsEventsMinimize: Boolean = _

  @Value("${use.offline.imports:false}")
  var useOfflineImports: Boolean = _

  @Value("${data.allowed.root.paths}")
  var allowedRootPaths: Array[String] = _

  @Value("${capex.root.path}")
  var capexRootPath: String = _

  def addCommonPageObjects(mav: ModelAndView, user: Principal, cache: CacheService,
                           parquetService: ParquetService): Unit = {
    mav.addObject("version", buildVersion)
    mav.addObject("capexPageEnabled", capexPageEnabled)
    mav.addObject("jsEventsMinimize", jsEventsMinimize)
    mav.addObject("user", user)
    val dbUser = cache.users.get().users(user.getName)
    mav.addObject("colorMode", dbUser.colorMode)
    mav.addObject("isSparkAlive", parquetService.isSparkAlive)
    mav.addObject("offlineImport", if (useOfflineImports) "Offline" else "")
  }

}
