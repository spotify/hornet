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

package com.spotify.hornet.run

import collection.JavaConversions._

import scala.collection.mutable.MutableList

import java.util.HashMap

import org.scalatest.FunSuite
import org.scalatest.Assertions.assert
import org.scalatest.BeforeAndAfter

import org.junit.Test
import org.junit.Assert

import com.spotify.hornet.run.HornetSampleTest
import com.spotify.hornet.run.LoadTestRunnerTestSampleTest
import com.spotify.hornet.run.HornetSampleTimedTest
import com.spotify.hornet.run.LoadTestRunner

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.SynchronizedBuffer

import com.spotify.hornet.record.RecordWriter
import com.spotify.hornet.record.Record

import com.spotify.hornet.log.Logger

/**
 * Logger mock captures error messages so that the test can validate them
 */
class LoggerMock extends Logger {

  val msgs = MutableList[String]()
  val exs = MutableList[Throwable]()

  def reset() {
    msgs.clear()
    exs.clear()
  }

  override def error(msg: String, ex: Throwable) {
    msgs += msg
    exs += ex
  }
}

class IntervalTest extends FunSuite {

  test("milliseconds") {
    // val i1 = new Interval(1000)
    // assert(1000 == i1.milliseconds)
    // assert("1000ms" == i1.toString)
    // val i2 = Interval("1000")
    // assert(1000 == i2.milliseconds)
    // assert("1000ms" == i2.toString)
    val i3 = Interval("1000ms")
    assert(1000 == i3.milliseconds)
    assert("1000ms" == i3.toString)
  }

  test("seconds") {
    val i = Interval("20s")
    assert(20 * 1000 == i.milliseconds)
    assert("20s" == i.toString)
  }

  test("minutes") {
    val i = Interval("5m")
    assert(5 * 60 * 1000 == i.milliseconds)
    assert("5m" == i.toString)
  }

  test("hours") {
    val i = Interval("2h")
    assert(2 * 60 * 60 * 1000 == i.milliseconds)
    assert("2h" == i.toString)
  }

  test("invalid") {
    tryInvalidInterval("", "'' is not a valid interval")
    tryInvalidInterval("abcd", "'abcd' is not a valid interval")
    tryInvalidInterval("10x", "'10x' is not a valid interval")
  }

  def tryInvalidInterval(s: String, m: String) {
    try {
      Interval(s)
      fail("Expected IllegalArgumentException")
    } catch {
      case ex:IllegalArgumentException => assert(m == ex.getMessage)
    }
  }
}


/**
 * An in-memory implementation of RecordWriter which is useful for testing.
 */
class RecordWriterMock extends RecordWriter {

  val records = new ArrayBuffer[Record] with SynchronizedBuffer[Record]

  override def write(record: Record) {
    records += record
  }

  override def close() {}

}

class JUnitLoadTestTimedRunnerTest extends FunSuite with BeforeAndAfter {

  val logMock = new LoggerMock

  before {
    HornetSampleTest.initCounters()
    HornetSampleTimedTest.initCounters()
    HornetSamplePauseTest.initCounters()
    logMock.reset()
    Logger.log = logMock
    //Logger.verbose = true
  }

  after {
    Logger.log = new Logger
  }

  test("sample test") {
    HornetSampleTest.failTest = true
    val userConfig = Map("k1" -> Some("v1"), "k2" -> None)
    val expectedUserConfig = new java.util.HashMap[String, String]()
    expectedUserConfig.put("k1", "v1")
    expectedUserConfig.put("k2", null)
    val recordWriter = new RecordWriterMock()
    val runner = new JUnitLoadTestTimedRunner(classOf[HornetSampleTest], 100, 100, Interval("100ms"), recordWriter, userConfig)
    runner.run()
    // config should be called once
    assert(1 == HornetSampleTest.configCount)
    assert(expectedUserConfig == HornetSampleTest.config)
    // Suite setup and teardown should run once
    assert(1 == HornetSampleTest.suiteSetupCount)
    assert(1 == HornetSampleTest.suiteTeardownCount)
    // The test should run 10 times with 2 tests so 20 altogether.
    assert(Math.abs(20 - HornetSampleTest.testCount) < 3, "20 did not equal " + HornetSampleTest.testCount)
    // For each test run, setup/theardown should run once.
    assert(HornetSampleTest.testCount == HornetSampleTest.setupCount)
    assert(HornetSampleTest.testCount == HornetSampleTest.teardownCount)
    // And we should have a record for each test run.
    assert(HornetSampleTest.testCount == recordWriter.records.size)
    var successCount = 0
    var failedCount = 0
    for (r <- recordWriter.records) {
      if (r.successful) {
        successCount += 1
      } else {
        failedCount += 1
      }
    }
    // About half of the tests should succeed and half should fail.  This is not exact depending
    // on how many ran.
    assert(HornetSampleTest.testCount - successCount * 2 <= 10,
      "testCount: " + HornetSampleTest.testCount + ", successCount: " + successCount)
    assert(HornetSampleTest.testCount - failedCount * 2 <= 10,
      "testCount: " + HornetSampleTest.testCount + ", failedCount: " + failedCount)
    HornetSampleTest.failTest = false
  }

  test("More than 1000 test") {
    val recordWriter = new RecordWriterMock()
    val runner = new JUnitLoadTestTimedRunner(classOf[HornetSampleTest], 2000, 2000, Interval("100ms"), recordWriter, Map())
    runner.run()
    // The test should run 10 times with 2 tests so 20 altogether.
    assert(HornetSampleTest.testCount > 0)
  }

  test("exception config") {
    HornetSampleTest.exConfig = true
    try {
      val recordWriter = new RecordWriterMock()
      val runner = new JUnitLoadTestTimedRunner(classOf[HornetSampleTest], 100, 100, Interval("100ms"), recordWriter, Map())
      runner.run()
      assert(0 == HornetSampleTest.testCount)
      assert(1 == logMock.exs.size)
      assert(logMock.exs(0).isInstanceOf[java.lang.reflect.InvocationTargetException])
    } finally {
      HornetSampleTest.exConfig = false
    }
  }

  test("exception test") {
    HornetSampleTest.exTest = true
    try {
      val recordWriter = new RecordWriterMock()
      val runner = new JUnitLoadTestTimedRunner(classOf[HornetSampleTest], 100, 100, Interval("100ms"), recordWriter, Map())
      runner.run()
      assert(0 < logMock.exs.size)
      // All tests got run.
      assert(0 < HornetSampleTest.testCount)
      assert(logMock.exs(0).isInstanceOf[RuntimeException])
    } finally {
      HornetSampleTest.exTest = false
    }
  }

  test("exception setup") {
    HornetSampleTest.exSetup = true
    try {
      val recordWriter = new RecordWriterMock()
      val runner = new JUnitLoadTestTimedRunner(classOf[HornetSampleTest], 100, 100, Interval("100ms"), recordWriter, Map())
      runner.run()
      assert(0 < logMock.exs.size)
      // None of the tests got run because we are failing setup
      assert(0 == HornetSampleTest.testCount)
      assert(logMock.exs(0).isInstanceOf[RuntimeException])
    } finally {
      HornetSampleTest.exSetup = false
    }
  }

  test("exception teardown") {
    HornetSampleTest.exTeardown = true
    try {
      val recordWriter = new RecordWriterMock()
      val runner = new JUnitLoadTestTimedRunner(classOf[HornetSampleTest], 100, 100, Interval("100ms"), recordWriter, Map())
      runner.run()
      assert(0 < logMock.exs.size)
         // All tests got run.
      assert(0 < HornetSampleTest.testCount)
      assert(logMock.exs(0).isInstanceOf[RuntimeException])
    } finally {
      HornetSampleTest.exTeardown = false
    }
  }

  test("timed test") {
    val recordWriter = new RecordWriterMock()
    val runner = new JUnitLoadTestTimedRunner(test = classOf[HornetSampleTimedTest],
                                              requestsPerSecond = 10,
                                              poolSize = 10,
                                              cycleDuration = Interval("1s"),
                                              recordWriter,
                                              userConfig = Map())
    val start = System.currentTimeMillis
    runner.run()

    // The test should run multiple times.  Not sure how many because it is time driven.
    val testCount = HornetSampleTimedTest.testCount
    assert(Math.abs(testCount - 10) < 2, s"Wrong number of tests run ($testCount instead of 10)")
    // And we should have a record for each test run.
    assert(HornetSampleTimedTest.testCount == recordWriter.records.size, "should have a record for each test run")
    // Run for 1 second
    val testRuntime = System.currentTimeMillis - start
    assert(Math.abs(testRuntime - 1000) <= 150, s"Test took to long to run (${testRuntime}ms instead of ~1000ms)")
  }

  test("pause test") {
    HornetSamplePauseTest.sleepTimeMs = 1000
    val recordWriter = new RecordWriterMock()
    val runner = new JUnitLoadTestTimedRunner(classOf[HornetSamplePauseTest],
                                              requestsPerSecond = 10,
                                              poolSize = 10,
                                              cycleDuration = Interval("2s"),
                                              recordWriter = recordWriter,
                                              userConfig = Map())
    val start = System.currentTimeMillis
    runner.run()

    // Test interval is 2 second with 10 r/s, so we should get about 20 tests
    val testCount = HornetSamplePauseTest.testCount
    assert(Math.abs(testCount - 20) < 2, s"Wrong number of tests run ($testCount instead of 20")
    // And we should have a record for each test run.
    assert(HornetSamplePauseTest.testCount == recordWriter.records.size, "should have a record for each test run")
    // The test should run for 2s plus 1s for the last test pause.
    val testRuntime = System.currentTimeMillis - start
    assert(Math.abs(testRuntime - 3000) <= 100, s"Test took to long to run (${testRuntime}ms instead of ~3000ms)")
  }

}

class LoadTestRunnerTest extends FunSuite with BeforeAndAfter {

  val logMock = new LoggerMock

  before {
    LoadTestRunnerTestSampleTest.initCounters
    logMock.reset()
    Logger.log = logMock
    //Logger.verbose = true
  }

  after {
    Logger.log = new Logger
  }

  test("timed run") {
    val recordWriter = new RecordWriterMock()

    val start = System.currentTimeMillis
    LoadTestRunner.run(test=classOf[LoadTestRunnerTestSampleTest],
                       requestsPerSeconds=List(1, 2, 3),
                       poolSize=10,
                       cycleDuration=Interval("5s"),
                       cycleSleepDuration=Interval("1s"),
                       recordWriter=recordWriter,
                       userConfig=Map.empty)

    // The test should run about 30 times (5 + 10 + 15)
    val testCount = LoadTestRunnerTestSampleTest.testCount
    assert(Math.abs(testCount - 30) < 2, s"Wrong number of tests run ($testCount instead of 30)")
    // And we should have a record for each test run.
    assert(testCount == recordWriter.records.size, "should have a record for each test run")
    // Test ran for 18 second (5*3 + 2 breaks)
    val testRuntime = System.currentTimeMillis - start
    assert(Math.abs(testRuntime - 18000) <= 500, s"Test took to long to run (${testRuntime}ms instead of 18s)")
  }

}
