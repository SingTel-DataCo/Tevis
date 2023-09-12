package com.dataspark.networkds.util

import org.apache.spark.sql.{DataFrame, Row}

object DatasetUtil {

  def collect(df: DataFrame): Seq[AnyRef] = {
    val rowsToDisplay = df.collect()
    rowsToDisplay.map(row =>
      df.columns.map(col => col -> format(row.get(row.fieldIndex(col)))).toMap
    ).toSeq
  }

  def format(value: Any): Any = {
    value match {
      case array: Seq[_] => array.map(format)
      case struct: Row => struct.schema.fields.map(_.name).map(fname =>
                            fname -> format(struct.get(struct.fieldIndex(fname)))
                          ).toMap
      case map: Map[_, _] => map.map{case (k, v) => k -> format(v)}
      case _ => value
    }
  }
}
