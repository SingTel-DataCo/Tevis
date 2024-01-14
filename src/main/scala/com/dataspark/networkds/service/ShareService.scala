package com.dataspark.networkds.service

import com.dataspark.networkds.beans.{Section, Tab, Workbook}
import com.dataspark.networkds.util.Str
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ShareService {

  @Autowired
  private var cache: CacheService = _

  def copySharedTabOrSection(shareId: String, username: String) = {
    val tokens = Str.decodeBase64(shareId).split(":")
    val sourceUser = tokens(1)
    // If you own the link you don't need to copy it
    if (sourceUser != username) {
      val tabId = tokens(2)
      val sectionId = if (tokens.length > 3) tokens(3) else null

      val sourceUserTabs = cache.getUserData(sourceUser).get().workbook.tabs
      if (sourceUserTabs.contains(tabId)) {
        val tab = sourceUserTabs(tabId)
        val currUserData = cache.getUserData(username)
        val currUserWb = currUserData.get().workbook
        val wb = if (sectionId == null) {
          copySharedTab(sourceUser, tab, currUserWb)
        } else {
          val sourceSections = tab.sections
          if (sourceSections.contains(sectionId)) {
            copySharedSection(sourceUser, sourceSections(sectionId), currUserWb)
          } else {
            throw new IllegalArgumentException("Link has expired.")
          }
        }
        currUserData.get().workbook = wb
        currUserData.save()
      } else {
        throw new IllegalArgumentException("Link has expired.")
      }
    }
  }

  def copySharedTab(sourceUser: String, tab: Tab, destWb: Workbook): Workbook = {
    val newId = destWb.tabCounter + 1
    val newTabId = "#s-tab" + newId
    val newTabContentId = "#s-tab-content" + newId
    val newSectionIdStart = destWb.sectionCounter
    val newSectionIds = (newSectionIdStart + 1 to newSectionIdStart + tab.sectionOrder.size)
      .map("#shared-section" + _).toList
    val oldNewIdMap = tab.sectionOrder.zip(newSectionIds).toMap
    val newSections = tab.sections.map(k => oldNewIdMap(k._1) -> k._2.copy(oldNewIdMap(k._1)))
    val newTab = tab.copy(tabId = newTabId, tabContentId = newTabContentId, sections = newSections, sectionOrder = newSectionIds,
      currentSection = oldNewIdMap(tab.currentSection))
    destWb.copy(currentTab = newTab.tabId, tabs = destWb.tabs ++ Map(newTab.tabId -> newTab),
      tabOrder = destWb.tabOrder ++ List(newTab.tabId), tabCounter = newId,
      sectionCounter = newSectionIdStart + tab.sectionOrder.size)
  }

  def copySharedSection(sourceUser: String, section: Section, destWb: Workbook): Workbook = {
    val tab = if (destWb.tabs.contains("#sq-tab")) destWb.tabs("#sq-tab") //shared queries tab
    else Tab("#sq-tab", "#sq-tab-content", false, "Shared", List.empty, null, Map.empty)
    val newId = destWb.sectionCounter + 1
    val newSectionId = "#shared-section" + newId
    val sectionClone = section.copy(sectionId = newSectionId)
    val newTab = tab.copy(sectionOrder = List(newSectionId) ++ tab.sectionOrder,
      currentSection = newSectionId, sections = tab.sections ++ Map(newSectionId -> sectionClone))
    destWb.copy(currentTab = newTab.tabId, tabs = destWb.tabs ++ Map(newTab.tabId -> newTab),
      tabOrder = (destWb.tabOrder ++ List(newTab.tabId)).distinct,
      sectionCounter = newId)
  }
}
