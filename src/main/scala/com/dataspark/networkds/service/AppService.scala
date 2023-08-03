package com.dataspark.networkds.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AppService {

  @Value("${build.version}")
  var buildVersion: String = _

  @Value("${admin.email}")
  var adminEmail: String = _

  @Value("${capex.page.enabled:false}")
  var capexPageEnabled: Boolean = _

  @Value("${js_events.minimize:false}")
  var jsEventsMinimize: Boolean = _

  @Value("${data.root.path}")
  var dataRootPath: String = _

  @Value("${capex.root.path}")
  var capexRootPath: String = _
}
