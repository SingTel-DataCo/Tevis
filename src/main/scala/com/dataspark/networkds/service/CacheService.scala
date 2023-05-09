package com.dataspark.networkds.service

import com.dataspark.networkds.beans._
import com.dataspark.networkds.dao.{JsonDb, JsonDbFactory}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

import scala.collection.{Map, mutable}

@Service
class CacheService {

  @Autowired
  var passwordEncoder: PasswordEncoder = _

  def getUserData(username: String): JsonDb[UserData] = {

    val jsonDb = JsonDbFactory.getInstance(s"userData_$username.json", classOf[UserData])
    if (jsonDb.get() == null) {
      jsonDb.set(UserData()) //Workaround for saving case class instances in Scala
      jsonDb.save()
    }
    jsonDb
  }

  def capexDirs: JsonDb[mutable.Map[String, AnyRef]] = {
    val jsonDb = JsonDbFactory.getInstance("capexDirs.json", classOf[mutable.Map[String, AnyRef]])
    if (jsonDb.get() == null) {
      jsonDb.set(mutable.Map())
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

  def getParquetDirOrElseUpdate(key: String, valueGenerator: () => Map[String, HFile]): Map[String, HFile] = {
    val dirs = parquetDirs.get()
    if (!dirs.dirs.contains(key)) {
      parquetDirs.get().dirs.put(key, valueGenerator.apply())
      parquetDirs.save()
    }
    parquetDirs.get().dirs(key)
  }

  def getAllExistingTablesStartingWith(tblName: String): Seq[String] = {
    parquetDirs.get().dirs.keys
      .flatMap(parquetDirs.get().dirs(_).asInstanceOf[Map[String, Any]].keys)
      .filter(_.matches(tblName + "|" + tblName + "_\\d+")).toSeq
  }

  def getParquetDir(key: String): Map[String, HFile] = parquetDirs.get().dirs(key)

  def getCapexDirOrElseUpdate(key: String, valueGenerator: () => AnyRef): AnyRef = {
    if (!capexDirs.get().contains(key)) {
      capexDirs.get().put(key, valueGenerator.apply())
      capexDirs.save()
    }
    capexDirs.get()(key)
  }

  def putParquetDir(key: String, list: Map[String, HFile]): Unit = {
    parquetDirs.get().dirs.put(key, list)
    parquetDirs.save()
  }

  def getCapexDir(key: String): AnyRef = capexDirs.get()(key)

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
