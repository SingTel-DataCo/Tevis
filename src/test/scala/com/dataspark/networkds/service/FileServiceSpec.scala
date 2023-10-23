package com.dataspark.networkds.service

import com.dataspark.networkds.beans.{HFileData, QueryTx}
import com.dataspark.networkds.util.E2EVariables
import org.apache.commons.io.FileUtils
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpec}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}

import java.io.File
import java.sql.Timestamp
import java.time.Instant
import scala.collection.immutable.ListMap

class FileServiceSpec extends WordSpec with Matchers with BeforeAndAfterAll with Eventually {

  val testDataDir = "./test_data"
  val fileService = new FileService

  override def beforeAll: Unit = {
    fileService.dataDir = testDataDir
  }

  override def afterAll: Unit = {
    FileUtils.deleteQuietly(new File(testDataDir))
  }

  def generateQueryTx(id: String = "query123"): QueryTx = {
    QueryTx(
      queryId = id,
      date = Timestamp.from(Instant.now()), // Assuming you want the current timestamp
      user = "testUser",
      query = HFileData(
        data = Map("key" -> "value").asInstanceOf[AnyRef], // Example data, adjust as needed
        format = "json",
        path = s"/data/$id.json",
        schema = ListMap("column1" -> 1, "column2" -> "value2"), // Example schema, adjust as needed
        sql = "SELECT * FROM table"
      )
    )
  }

  "writeQueryTxAsync" should {
    "write a QueryTx to a file" in {
      val queryTx = generateQueryTx()

      fileService.writeQueryTxAsync(queryTx)

      eventually(timeout(Span(5, Seconds)), interval(Span(100, Millis))) {
        val fileContent = scala.io.Source.fromFile(s"$testDataDir/query123.json").mkString
        fileContent should include("\"key\":\"value\"")
      }
    }
  }

  "writeCapexDirAsync" should {
    "write a Capex directory info to a file" in {
      val capexDirInfo = Map("key" -> "value")

      val dir = "dir123"
      fileService.writeCapexDirAsync(dir, capexDirInfo)

      eventually(timeout(Span(5, Seconds)), interval(Span(100, Millis))) {
        val fileContent = scala.io.Source.fromFile(s"$testDataDir/capex/dag_${dir.hashCode}.json").mkString
        fileContent should include("\"key\":\"value\"")
      }
    }
  }

  "readCapexDags" should {
    "read Capex DAGs from files" in {

      val capexDirInfo = Map("runners" -> Map("key" -> "value"))
      val dir = "dir123"
      fileService.writeCapexDirAsync(dir, capexDirInfo)
      eventually(timeout(Span(5, Seconds)), interval(Span(100, Millis))) {
        // Assuming you have some capex files in the capex directory for testing
        val capexDags = fileService.readCapexDags()
        capexDags should not be empty
      }
    }
  }

  "readQueryTx" should {
    "read a QueryTx from a file" in {

      val queryTx = generateQueryTx()
      val queryId = queryTx.queryId
      val file = new File(s"$testDataDir/$queryId.json")
      E2EVariables.objectMapper.writeValue(file, queryTx)

      val result = fileService.readQueryTx(queryId)

      result shouldBe Some(queryTx)
    }

    "return None if the file doesn't exist" in {
      val queryId = "nonexistentquery"

      val result = fileService.readQueryTx(queryId)

      result shouldBe None
    }
  }

  "deleteQueryFilesExcept" should {
    "delete query files except those in the exclude list" in {

      val queryTx = generateQueryTx("user_20231023_183401")
      val queryTx2 = generateQueryTx("user_20231023_183402")
      val queryTx3 = generateQueryTx("user_20231023_183403")

      fileService.writeQueryTxAsync(queryTx)
      fileService.writeQueryTxAsync(queryTx2)
      fileService.writeQueryTxAsync(queryTx3)

      eventually(timeout(Span(5, Seconds)), interval(Span(100, Millis))) {
        val excludeFiles = Seq("user_20231023_183401.json", "user_20231023_183402.json")

        // Assuming you have some query files in the test directory for testing
        val deletedCount = fileService.deleteQueryFilesExcept(excludeFiles)

        deletedCount should be > 0
      }

    }
  }
}