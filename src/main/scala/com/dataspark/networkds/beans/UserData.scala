package com.dataspark.networkds.beans

import java.util.Date
import scala.collection.Map
import scala.collection.immutable.ListMap
import scala.collection.mutable
import scala.collection.mutable.{Buffer, Set}

case class Users(var users: mutable.Map[String, UserInfo] = mutable.Map())

case class UserInfo(username: String, password: String, roles: Seq[String], isDisabled: Boolean = false,
                    lastCreated: Date = new Date(), lastLogin: Date = new Date())

case class UserData(var capexDir: String = null, var parquetDir: String = null, var capexDirHistory: mutable.Set[String] = mutable.Set(),
  var datasets: Set[String] = Set(), var sqlHistory: Buffer[String] = Buffer(), var workbook: Workbook = Workbook())

case class Workbook(tabOrder: List[String] = List(), currentTab: String = "", tabs: Map[String, Tab] = Map.empty,
                    tabCounter: Int = 0, sectionCounter: Int = 0)

case class Tab(tabId: String, tabContentId: String, isMasterTab: Boolean, tabName: String, sectionOrder: List[String], currentSection: String, sections: Map[String, Section])

case class Section(sectionId: String, sectionName: String, description: String, sql: String, queryId: String, showChart: Boolean, chartModel: ChartModel)

case class ChartModel(chartType: String, selectedColumns: Seq[String], extraOptions: Map[String, Any])

case class ParquetDirs(dirs: mutable.Map[String, Map[String, HFile]] = mutable.Map())

case class HFile(path: String, size: Long,
                 var table: String = "", var format: String = "parquet",
                 var schema: ListMap[String, Any] = null)

case class QueryTx(queryId: String, date: Date, user: String, query: HFileData)

case class HFileData(data: AnyRef, format: String, path: String, schema: ListMap[String, Any], sql: String)