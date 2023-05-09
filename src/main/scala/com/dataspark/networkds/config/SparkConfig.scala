package com.dataspark.networkds.config

import org.apache.sedona.sql.utils.SedonaSQLRegistrator
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.{Bean, Configuration}

import java.util.Properties

@Configuration
class SparkConfig {
  @Value("${spark.app.name}") var appName: String = _
  @Value("${spark.master}") var masterUri: String = _

  @ConfigurationProperties(prefix = "spark")
  @Bean
  def sparkProps = new Properties

  @ConfigurationProperties(prefix = "hadoop")
  @Bean
  def hadoopProps = new Properties

  def spark: SparkSession = {
    val propKeys: Seq[String] = sparkProps.keySet().toArray.filter(x => !Seq("app.name", "master").contains(x)).toSeq.map(_.toString)
    val builder = SparkSession
      .builder()
      .master(masterUri)
      .appName(appName)
    val builder2 = propKeys.foldLeft(builder)((b, prop) => b.config("spark." + prop, sparkProps.getProperty(prop)))
    val builder3 = hadoopProps.keySet().toArray().foldLeft(builder2)((b, prop) => b.config("hadoop." + prop, hadoopProps.getProperty(prop.toString)))
    val sparkSession = builder3.getOrCreate()
    SedonaSQLRegistrator.registerAll(sparkSession)
    sparkSession
  }

}