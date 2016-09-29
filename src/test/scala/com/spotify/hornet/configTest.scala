/*
 * Copyright 2016 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.hornet.config

import java.io.File
import java.text.SimpleDateFormat

import org.scalatest.FunSuite
import org.scalatest.Assertions.assert
import org.scalatest.Matchers._

import com.spotify.hornet.run.Interval
import com.spotify.hornet.config.ConfigReader._
import com.spotify.hornet.config.RunType


class ConfigTest extends FunSuite {

  test("record file") {
    val o = new Options()
    o.recordFile = new File("/tmp/record.log")
    assert(new File("/tmp/record.log") == o.recordFile)
  }

  test("record dir") {
    val o = new Options()
    o.runType = RunType.Test
    o.recordDir = new File("/tmp")
    o.test = Class.forName("java.lang.String")
    assert(new File("/tmp") == o.recordDir)
    o.recordFile.toString should fullyMatch regex """/tmp/hornet_record_\d\d\d\d\-\d\d\-\d\d_\d\d\-\d\d-\d\d\.log"""
    o.textReportFile.toString should fullyMatch regex """/tmp/hornet_report_\d\d\d\d\-\d\d\-\d\d_\d\d\-\d\d-\d\d\.txt"""
  }

  test("report record file and dir") {
    val o = new Options()
    o.recordDir = new File("/tmp")
    o.recordFile = new File("/tmp/record.log")
    o.test = Class.forName("java.lang.String")
    assert(new File("/tmp") == o.recordDir)
    assert(new File("/tmp/record.log") == o.recordFile)
    o.textReportFile.toString should fullyMatch regex """/tmp/hornet_report_\d\d\d\d\-\d\d\-\d\d_\d\d\-\d\d-\d\d\.txt"""
  }

  test("report date from record file name") {
    val df = new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss")
    val d = "2015-11-14_12-32-42"
    val o = new Options()
    o.runType = RunType.Report
    o.recordDir = new File("/tmp")
    o.recordFile = new File(s"/tmp/hornet_record_$d.log")
    assert(df.parse(d) == o.reportDate)
    assert(s"/tmp/hornet_report_$d.txt" == o.textReportFile.toString)
  }

  test("report date no date in record file name") {
    val o = new Options()
    o.runType = RunType.Report
    o.recordFile = new File("/tmp/hornet_record.log")
    o.textReportFile.toString should fullyMatch regex """/tmp/hornet_report_\d\d\d\d\-\d\d\-\d\d_\d\d\-\d\d-\d\d\.txt"""
    assert(null != o.reportDate)
  }

  test("report date invalid date in record file name") {
    val o = new Options()
    o.runType = RunType.Report
    o.recordFile = new File("/tmp/hornet_record_2015-11-14.log")
    o.textReportFile.toString should fullyMatch regex """/tmp/hornet_report_\d\d\d\d\-\d\d\-\d\d_\d\d\-\d\d-\d\d\.txt"""
    assert(null != o.reportDate)
  }

}

class ConfigReaderParseParamsTest extends FunSuite {

  test("empty") {
    val m = parseParams(List[String]())
    assert(m.isEmpty)
  }

  test("params") {
    assert(Map("first" -> Some("one")) == parseParams(List("--first", "one")))
    assert(Map("first" -> Some("one"), "second" -> Some("two")) == parseParams(List("--first", "one", "--second", "two")))
  }

  test("flags") {
    assert(Map("first" -> None) == parseParams(List("--first")))
    assert(Map("first" -> None, "second" -> None) == parseParams(List("--first", "--second")))
  }

  test("mixed") {
    assert(Map("first" -> None, "second" -> Some("two")) == parseParams(List("--first", "--second", "two")))
    assert(Map("first" -> Some("one"), "second" -> None) == parseParams(List("--first", "one", "--second")))
    assert(Map("first" -> Some("one"), "second" -> None, "third" -> Some("three")) ==
      parseParams(List("--first", "one", "--second", "--third", "three")))
    assert(Map("first" -> None, "second" -> Some("two"), "third" -> None) ==
      parseParams(List("--first", "--second", "two", "--third")))
  }

  test("type") {
    assert(Map("type" -> Some("test")) == parseParams(List("test")))
    assert(Map("type" -> Some("test"), "first" -> Some("one"), "second" -> Some("two")) ==
      parseParams(List("test", "--first", "one", "--second", "two")))
    assert(Map("type" -> Some("test"), "first" -> None, "second" -> Some("two"), "third" -> None) ==
      parseParams(List("test", "--first", "--second", "two", "--third")))
  }

}

class ConfigReaderParsePropertiesTest extends FunSuite {

  test("emtpy") {
    assert(Map() == parseProperties(Map()))
  }

  test("key value") {
    assert(Map("first" -> Some("one")) == parseProperties(Map("first" -> "one")))
    assert(Map("first" -> Some("one"), "second" -> Some("two")) == parseProperties(Map("first" -> "one", "second" -> "two")))
  }

  test("key none") {
    assert(Map("first" -> None) == parseProperties(Map("first" -> "")))
    assert(Map("first" -> None, "second" -> None) == parseProperties(Map("first" -> "", "second" -> "")))
  }

  test("mixed") {
    assert(Map("first" -> Some("one"), "second" -> None, "third" -> Some("three")) ==
      parseProperties(Map("first" -> "one", "second" -> "", "third" -> "three")))
  }
}

class ConfigReaderMergeTest extends FunSuite {

  test("merge") {
    val c1 = Map("first" -> Some("one"), "second" -> Some("two"))
    val c2 = Map("second" -> Some("TWO"), "third" -> Some("three"))
    val c3 = merge(c2, c1)
    assert(Map("first" -> Some("one"), "second" -> Some("TWO"), "third" -> Some("three")) == c3)
  }
}


class ConfigReaderParseRequestsPerSecondsTest extends FunSuite {

  test("empty") {
    assert(Nil == parseRequestsPerSeconds(""))
  }

  test("one") {
    assert(List(1) == parseRequestsPerSeconds("1"))
  }

  test("many") {
    assert(List(1, 2, 3) == parseRequestsPerSeconds("1:2:3"))
  }

  test("range") {
    assert(List(1, 2, 3, 4, 5) == parseRequestsPerSeconds("1-1-5"))
    assert(List(10, 12, 14) == parseRequestsPerSeconds("10-2-15"))
  }

  test("missing") {
    assert(List(1, 2, 3) == parseRequestsPerSeconds(":1::2:3"))
  }

  test("invalid") {
    intercept[NumberFormatException] {
      parseRequestsPerSeconds("1:2:a")
    }
  }

  test("invalid range") {
    intercept[IllegalArgumentException] {
      parseRequestsPerSeconds("1-2")
    }
    intercept[IllegalArgumentException] {
      parseRequestsPerSeconds("1-2-3-4")
    }
  }

}


class ConfigReaderParseOptionsTest extends FunSuite {

  test("valid test options with record file") {
    val m = parseOptions(
      Map(
        "type"                 -> Some("test"),
        "test"                 -> Some("java.lang.String"),
        "cycle-duration"       -> Some("10s"),
        "cycle-sleep-duration" -> Some("30s"),
        "requests-per-seconds" -> Some("1:2:3"),
        "pool-size"            -> Some("3"),
        "record-file"          -> Some("/tmp/record.log"),
        "verbose"              -> None))
    assert(m.runType == RunType.Test)
    assert(m.test == Class.forName("java.lang.String"))
    assert(m.cycleDuration.milliseconds == (10 * 1000))
    assert(m.cycleSleepDuration.milliseconds == (30 * 1000))
    assert(m.requestsPerSeconds == List(1, 2, 3))
    assert(m.poolSize == 3)
    assert(m.recordFile == new File("/tmp/record.log"))
    assert(m.verbose == true)
  }

  test("valid test options with record dir") {
    val m = parseOptions(
      Map(
        "type"                 -> Some("test"),
        "test"                 -> Some("java.lang.String"),
        "cycle-duration"       -> Some("10s"),
        "cycle-sleep-duration" -> Some("30s"),
        "requests-per-seconds" -> Some("1-1-3"),
        "pool-size"            -> Some("3"),
        "record-dir"           -> Some("/tmp"),
        "verbose"              -> None))
    assert(m.runType == RunType.Test)
    assert(m.test == Class.forName("java.lang.String"))
    assert(m.cycleDuration.milliseconds == (10 * 1000))
    assert(m.cycleSleepDuration.milliseconds == (30 * 1000))
    assert(m.requestsPerSeconds == List(1, 2, 3))
    assert(m.poolSize == 3)
    assert(m.recordDir == new File("/tmp"))
    assert(m.verbose == true)
    m.recordFile.toString should fullyMatch regex """/tmp/hornet_record_\d\d\d\d\-\d\d\-\d\d_\d\d\-\d\d-\d\d\.log"""
  }

  test("default verbose") {
    val m = parseOptions(
      Map(
        "type"                 -> Some("test"),
        "test"                 -> Some("java.lang.String"),
        "cycle-duration"       -> Some("10s"),
        "cycle-sleep-duration" -> Some("30s"),
        "requests-per-seconds" -> Some("1:2:3"),
        "pool-size"            -> Some("3"),
        "record-file"          -> Some("/tmp/record.log")))
    assert(m.runType == RunType.Test)
    assert(m.test == Class.forName("java.lang.String"))
    assert(m.cycleDuration.milliseconds == (10 * 1000))
    assert(m.cycleSleepDuration.milliseconds == (30 * 1000))
    assert(m.requestsPerSeconds == List(1, 2, 3))
    assert(m.poolSize == 3)
    assert(m.verbose == false)
  }

  test("valid report options") {
    val m = parseOptions(
      Map(
        "type"                 -> Some("report"),
        "record-file"          -> Some("/tmp/record.log")))
    assert(m.runType == RunType.Report)
    assert(m.recordFile == new File("/tmp/record.log"))
    m.textReportFile.toString should fullyMatch regex """/tmp/hornet_report_\d\d\d\d\-\d\d\-\d\d_\d\d\-\d\d-\d\d\.txt"""
  }

  test("missing everything") {
    val ex = intercept[IllegalArgumentException] {
      parseOptions(Map())
    }
    assert(ex.getMessage == """Must specify run type (one of test or report)""")
  }

  test("wrong type") {
    val ex = intercept[IllegalArgumentException] {
      parseOptions(Map("type" -> Some("wrong")))
    }
    assert(ex.getMessage == """Must specify run type (one of test or report)""")
  }

}

class ConfigReaderLoadTest extends FunSuite {

  test("config file") {
    val m = ConfigReader.load(Array("test", "--config", "src/test/resources/config.cfg",
      "--requests-per-seconds", "10:20:30", "--pool-size", "30", "--cycle-duration", "30s"))
    assert(m.test == Class.forName("java.lang.String"))
    assert(m.cycleDuration.milliseconds == (30 * 1000))
    assert(m.cycleSleepDuration.milliseconds == (30 * 1000))
    assert(m.requestsPerSeconds == List(10, 20, 30))
    assert(m.poolSize == 30)
    assert(m.verbose == false)
    val c = Map("key1" -> Some("val1"), "key2" -> Some("val2"))
    assert(m.userConfig == c)
    m.recordFile.toString should fullyMatch regex """/tmp/hornet_record_\d\d\d\d\-\d\d\-\d\d_\d\d\-\d\d-\d\d\.log"""
    m.textReportFile.toString should fullyMatch regex """/tmp/hornet_report_\d\d\d\d\-\d\d\-\d\d_\d\d\-\d\d-\d\d\.txt"""
  }

}
