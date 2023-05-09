package com.dataspark.networkds.service

import com.dataspark.networkds.beans.QueryTx
import com.dataspark.networkds.util.E2EVariables
import org.apache.commons.io.FileUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

import java.io.{File, FilenameFilter}

@Service
class FileService {

  @Value("${data_dir}")
  var dataDir: String = _

  def writeToFileAsync(queryTx: QueryTx): Unit = {
    new Thread{
      override def run: Unit = {
        FileUtils.write(new File(dataDir + "/" + queryTx.queryId + ".json"),
          E2EVariables.objectMapper.writeValueAsString(queryTx), "UTF-8")
      }
    }.start()
  }

  def readQueryTx(queryId: String): Option[QueryTx] = {
    val file = new File(dataDir + "/" + queryId + ".json")
    if (file.exists()) {
      Some(E2EVariables.objectMapper.readValue(file, classOf[QueryTx]))
    } else {
      None
    }
  }

  def deleteQueryFilesExcept(excludeFilesFromPurging: Seq[String]): Int = {
    val queryFiles = new File(dataDir).listFiles(new FilenameFilter() {
      def accept(dir: File, fileName: String): Boolean = fileName.matches(".*_\\d{8}_\\d{6}.json") &&
        !excludeFilesFromPurging.contains(fileName)
    })
    queryFiles.foreach(f => f.delete())
    queryFiles.length
  }


}
