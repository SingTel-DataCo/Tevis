package com.dataspark.networkds.util

import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.apache.spark.sql.types.{StringType, StructField, StructType}

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.collection.immutable.ListMap
import scala.io.Source

/**
 * Copied from DS_app_networkDS/planner project, package com.dataspark.telco.util
 */
object Str {

  /**
   * To read a delimited file and convert the file to Spark Dataframe
   *
   * @param spark                      : Spark Session
   * @param filePath                : input file to be parsed and created as Dataset
   * @param delim                      :  Delimiter value should be passed like "," or "\\|" to parse the line
   * @param fileOnClassPath to get file path from source
   * @return DataFrame
   */
  def parseSeqStringToDF(spark: SparkSession, filePath: String, delim: String):
  DataFrame = {
    val lines = parseCsvFile(filePath)
    val headers = lines.head.split(delim).map(StructField(_, StringType, true))
    val schema = StructType(headers)
    val seqRow = spark.sparkContext.parallelize(lines.tail.map(parseLine(_, delim))).map(Row.fromSeq(_))
    spark.createDataFrame(seqRow, schema)
  }

  def parseSeqStringToMap(filePath: String, delim: String): Seq[Map[String, AnyRef]] = {
    val lines = parseCsvFile(filePath)
    val headers = lines.head.split(delim)
    lines.tail.map(r => ListMap(parseLine(r, delim).zipWithIndex.map{
      case (cell, idx) =>
        val key = if (idx < headers.length) headers(idx) else "c_" + idx
        (key, cell)
    }: _*))
  }

  /**
   * Parse line by the input delimiter to get sequence of strings
   *
   * @param line    : line to be parsed
   * @param delim  : column will be parsed by this input delimter
   * @return sequence of strings
   */
  def parseLine(line: String, delim: String): Seq[String] = line.split(delim, -1).map(_.trim)

  def parseCsvLine(line: String): Seq[String] = line.split(",", -1).map(_.trim)

  def parseCsvFile(fileName: String): Seq[String] = {
    val enc = "UTF-8"
    val source = Source.fromFile(fileName, enc)
    val lines = try {
      source.getLines().filter(_.trim.nonEmpty).toList
    } finally {
      source.close()
    }
    lines
  }

  /**
   * Encode to base64, then encode further.
   * @param text
   * @return
   */
  def encodeBase64(text: String): String = {
    val encoded = Base64.getEncoder.encodeToString(text.getBytes(StandardCharsets.UTF_8))
    //Encode further by replacing = with $ and reversing the strings for every substring of length 5
    encoded.replace("=", "$").grouped(5).map(_.reverse).mkString
  }

  def decodeBase64(encoded: String): String = {
    val decodedPartial = encoded.replace("$", "=").grouped(5).map(_.reverse).mkString
    new String(Base64.getDecoder.decode(decodedPartial), StandardCharsets.UTF_8)
  }

  def formatFileSize(size: Long): String = formatSize(size)

  //Copied from: https://stackoverflow.com/a/24805871/3369952
  def formatSize(v: Long): String = {
    if (v < 1024) return v + " B"
    val z = (63 - java.lang.Long.numberOfLeadingZeros(v)) / 10
    val unit = " KMGTPE".charAt(z)
    f"${Math.scalb(v, z * -10)}%.1f ${unit}B"
  }
}
