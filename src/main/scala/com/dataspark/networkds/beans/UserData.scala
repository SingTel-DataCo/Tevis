package com.dataspark.networkds.beans

import java.sql.Timestamp
import scala.collection.immutable.ListMap
import scala.collection.{Map, mutable}
import scala.collection.mutable.{Buffer, Set}

case class Users(var users: mutable.Map[String, UserInfo] = mutable.Map())

case class UserInfo(username: String, password: String, roles: Seq[String], isDisabled: Boolean = false,
                    lastCreated: Timestamp = new Timestamp(System.currentTimeMillis()),
                    lastLogin: Timestamp = new Timestamp(System.currentTimeMillis()),
                    colorMode: String = "auto")

case class UserData(var capexDir: String = null, var parquetDir: String = null, var capexDirHistory: mutable.Set[String] = mutable.Set(),
  var datasets: Set[String] = Set(), var sqlHistory: Buffer[String] = Buffer(), var workbook: Workbook = Workbook())

case class Workbook(tabOrder: List[String] = List(), currentTab: String = "", tabs: Map[String, Tab] = Map.empty,
                    tabCounter: Int = 0, sectionCounter: Int = 0)

case class Tab(tabId: String, tabContentId: String, isMasterTab: Boolean, tabName: String, sectionOrder: List[String], currentSection: String, sections: Map[String, Section])

case class Section(sectionId: String, sectionName: String, description: String, sql: String, queryId: String, showChart: Boolean, chartModel: ChartModel)

case class ChartModel(chartType: String, selectedColumns: Seq[String], extraOptions: Map[String, Any])

case class ParquetDirs(dirs: mutable.Map[String, Map[String, HFile]] = mutable.Map())

//A hadoop folder with "_SUCCESS" file in it
case class HFile(path: String, size: Long,
                 var table: String = "", var format: String = "parquet",
                 var schema: ListMap[String, Any] = null)

//List of hadoop files and their file properties (e.g. timestamp, size)
case class DsFiles(dirs: mutable.Map[String, Seq[DsFile]] = mutable.Map())
case class DsFile(filename: String, date: Timestamp, size: Long, path: String = null)

case class QueryTx(queryId: String, date: Timestamp, user: String, query: HFileData)

case class HFileData(data: AnyRef, format: String, path: String, schema: ListMap[String, Any], sql: String,
                     lf: LargeFileInfo = null)
case class LargeFileInfo(bytesPerRow: Int = 0, rowLimit: Int = 0, csvPath: String = null, csvSize: String = null,
                         error: String = null)
