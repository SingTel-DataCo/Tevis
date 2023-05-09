package com.dataspark.networkds.util

import org.apache.hadoop.conf.Configuration
import org.apache.spark.SparkConf
import org.apache.log4j.LogManager


/**
 * All methods copied from spark-core-2.11 org.apache.spark.deploy.SparkHadoopUtil
 * since they are private methods and cannot be accessed from there.
 */
object SparkHadoopUtil {

  private val log = org.slf4j.LoggerFactory.getLogger(this.getClass)

  def newConfiguration(conf: SparkConf): Configuration = {
    val hadoopConf = new Configuration()
    appendS3AndSparkHadoopConfigurations(conf, hadoopConf)
    hadoopConf
  }

  def appendS3AndSparkHadoopConfigurations(
    conf: SparkConf,
    hadoopConf: Configuration): Unit = {
    // Note: this null check is around more than just access to the "conf" object to maintain
    // the behavior of the old implementation of this code, for backwards compatibility.

    if (conf != null) {
      // Explicitly check for S3 environment variables
      val keyId = System.getenv("AWS_ACCESS_KEY_ID")
      val accessKey = System.getenv("AWS_SECRET_ACCESS_KEY")
      val defaultFs = System.getenv("AWS_S3_FS")
      if (keyId != null && accessKey != null) {
        hadoopConf.set("fs.defaultFS", defaultFs)
        hadoopConf.set("fs.s3.awsAccessKeyId", keyId)
        hadoopConf.set("fs.s3.awsAccessKeyId", keyId)
        hadoopConf.set("fs.s3n.awsAccessKeyId", keyId)
        hadoopConf.set("fs.s3a.access.key", keyId)
        hadoopConf.set("fs.s3.awsSecretAccessKey", accessKey)
        hadoopConf.set("fs.s3n.awsSecretAccessKey", accessKey)
        hadoopConf.set("fs.s3a.secret.key", accessKey)

        val sessionToken = System.getenv("AWS_SESSION_TOKEN")
        if (sessionToken != null) {
          hadoopConf.set("fs.s3a.session.token", sessionToken)
        }
      }
      appendSparkHadoopConfigs(conf, hadoopConf)
      val bufferSize = conf.get("spark.buffer.size", "65536")
      hadoopConf.set("io.file.buffer.size", bufferSize)
    }
  }

  def appendSparkHadoopConfigs(conf: SparkConf, hadoopConf: Configuration): Unit = {
    // Copy any "spark.hadoop.foo=bar" spark properties into conf as "foo=bar"
    for ((key, value) <- conf.getAll if key.startsWith("spark.hadoop.")) {
      hadoopConf.set(key.substring("spark.hadoop.".length), value)
    }
  }
}
