package com.dataspark.networkds.service

import com.dataspark.networkds.beans._
import com.dataspark.networkds.dao.{JsonDb, JsonDbFactory}
import org.apache.hadoop.fs.Path
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.{Autowired, Value}
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

import java.util.Date
import scala.collection.{Map, mutable}

@Service
class CacheService {

  val log = LoggerFactory.getLogger(this.getClass.getSimpleName)

  @Autowired
  var passwordEncoder: PasswordEncoder = _

  @Value("${capex.cache.dags.timeout:60}")
  var capexCacheTimeout: Int = _

  def getDsFiles(path: String): Seq[DsFile] = {
    val resolvedPath = new Path(path).toString
    log.info(s"Resolved path $resolvedPath")
    val key = files.get().dirs.keys.find(_.endsWith(resolvedPath)).get
    files.get().dirs(key)
  }

  def getUserData(username: String): JsonDb[UserData] = {

    val jsonDb = JsonDbFactory.getInstance(s"userData_$username.json", classOf[UserData])
    if (jsonDb.get() == null) {
      jsonDb.set(UserData()) //Workaround for saving case class instances in Scala
      jsonDb.save()
    }
    jsonDb
  }

  def parquetDirs: JsonDb[ParquetDirs] = {
    val jsonDb = JsonDbFactory.getInstance("parquetDirs.json", classOf[ParquetDirs])
    if (jsonDb.get() == null) {
      jsonDb.set(ParquetDirs())
      jsonDb.save()
    }
    jsonDb
  }

  def files: JsonDb[DsFiles] = {
    val jsonDb = JsonDbFactory.getInstance("dsFiles.json", classOf[DsFiles])
    if (jsonDb.get() == null) {
      jsonDb.set(DsFiles())
      jsonDb.save()
    }
    jsonDb
  }

  val capexDirs: mutable.Map[String, (Date, Map[String, Any])] = mutable.Map()

  def getCapexDirOrElseUpdate(key: String, valueGenerator: () => Map[String, Any]): Map[String, Any] = {
    if (!capexDirs.contains(key) || new Date().getTime - capexDirs(key)._1.getTime > capexCacheTimeout * 1000) {
      capexDirs.put(key, (new Date(), valueGenerator.apply()))
    }
    capexDirs(key)._2
  }

  def getParquetDirOrElseUpdate(key: String, valueGenerator: () => Map[String, HFile]): Map[String, HFile] = {
    val jsonDb = parquetDirs.get()
    if (!jsonDb.dirs.contains(key)) {
      jsonDb.dirs.put(key, valueGenerator.apply())
      parquetDirs.save()
    }
    jsonDb.dirs(key)
  }

  def getAllExistingTablesStartingWith(tblName: String): Seq[String] = {
    parquetDirs.get().dirs.keys
      .flatMap(parquetDirs.get().dirs(_).asInstanceOf[Map[String, Any]].keys)
      .filter(_.matches(tblName + "|" + tblName + "_\\d+")).toSeq
  }

  def getParquetDir(key: String): Map[String, HFile] = parquetDirs.get().dirs(key)

  def putParquetDir(key: String, list: Map[String, HFile]): Unit = {
    parquetDirs.get().dirs.put(key, list)
    parquetDirs.save()
  }

  def adminUser = UserInfo("dataspark-admin", passwordEncoder.encode("dataspark-admin"), Seq("ADMIN"))

  def users: JsonDb[Users] = {
    val jsonDb = JsonDbFactory.getInstance("users.json", classOf[Users])
    if (jsonDb.get() == null) {
      val users = Users(mutable.Map(adminUser.username -> adminUser))
      jsonDb.set(users)
      jsonDb.save()
    }
    jsonDb
  }
}
