/*
 * Copyright © DataSpark Pte Ltd 2014 - 2022.
 *
 * This software and any related documentation contain confidential and proprietary information of
 * DataSpark and its licensors (if any). Use of this software and any related documentation is
 * governed by the terms of your written agreement with DataSpark. You may not use, download or
 * install this software or any related documentation without obtaining an appropriate licence
 * agreement from DataSpark. All rights reserved.
 */

package com.dataspark.networkds.util

import org.apache.spark.sql.DataFrame

import scala.collection.mutable.ListBuffer

object RunnerType extends Enumeration { val SparkJob, Python = Value }

case class ModuleRunner(shellScript: String, entryPointClass: String, configFilesNeeded: Seq[String], appConf: String,
  runnerType: RunnerType.Value = RunnerType.SparkJob, disabled: Boolean = false,
  profiles: ListBuffer[String] = ListBuffer.empty) {
  override def equals(obj: Any): Boolean =
    obj match {
      case runner: ModuleRunner =>
        this.shellScript.equals(runner.shellScript)
      case _ => false
    }

  override def hashCode(): Int = this.shellScript.hashCode
}
