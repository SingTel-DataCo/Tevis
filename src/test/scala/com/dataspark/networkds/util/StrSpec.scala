package com.dataspark.networkds.util

import org.apache.spark.sql.SparkSession
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

class StrSpec extends FlatSpec with BeforeAndAfterAll with Matchers {

  lazy val spark: SparkSession = {
    SparkSession
      .builder()
      .master("local")
      .appName("spark session")
      .config("spark.ui.enabled", "false")
      .config("spark.driver.host", "localhost")
      .config("spark.sql.autoBroadcastJoinThreshold", "-1")
      .config("spark.sql.shuffle.partitions", "1")
      .config("spark.appStateStore.asyncTracking.enable", "false")
      .config("spark.ui.liveUpdate.period", "-1")
      .config("spark.ui.dagGraph.retainedRootRDDs", "1")
      .config("spark.sql.ui.retainedExecutions", "1")
      .config("spark.ui.retainedJobs", "1")
      .config("spark.ui.retainedStages", "1")
      .config("spark.ui.retainedTasks", "1")
      .config("spark.ui.retainedDeadExecutors", "1")
      .config("spark.worker.ui.retainedExecutors", "1")
      .config("spark.worker.ui.retainedDrivers", "1")
      .config("spark.sql.codegen.wholeStage", "false")
      .getOrCreate()
  }

  "testDecodeBase64" should "test decode base 64" in {
  }

  "parseCapexDir" should "test decode base 64" in {
  }

}
