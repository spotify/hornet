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

package com.spotify.hornet.report

import java.io.ByteArrayOutputStream

import org.scalatest.FunSuite
import org.scalatest.Assertions.assert
import org.scalatest.BeforeAndAfter
import org.scalatest.mock._
import org.mockito.Mockito._

import com.spotify.hornet.record.Record
import com.spotify.hornet.record.RecordReader

class ReportTest extends FunSuite {

  test("min response time") {
    val report = new Report("test", 5)
    report.addRecord(new Record("test", 5, true, 1500, 2000))
    report.addRecord(new Record("test", 5, true, 2000, 2500))
    report.addRecord(new Record("test", 5, true, 2500, 2750))
    report.addRecord(new Record("test", 5, true, 3000, 3750))
    report.addRecord(new Record("test", 5, true, 100, 110))
    report.addRecord(new Record("test", 5, true, 100, 110))
    report.addRecord(new Record("test", 5, true, 100, 200))
    report.addRecord(new Record("test", 5, true, 100, 200))
    report.addRecord(new Record("test", 5, true, 1000, 1100))
    report.addRecord(new Record("test", 5, true, 1050, 1150))
    report.addRecord(new Record("test", 5, false, 1100, 1200))
    report.addRecord(new Record("test", 5, false, 1150, 1250))
    assert(10 == report.minResponseTime)
  }

  test("max response time") {
    val report = new Report("test", 5)
    report.addRecord(new Record("test", 5, true, 1500, 2000))
    report.addRecord(new Record("test", 5, true, 2000, 2500))
    report.addRecord(new Record("test", 5, true, 2500, 2750))
    report.addRecord(new Record("test", 5, true, 3000, 3750))
    report.addRecord(new Record("test", 5, true, 100, 110))
    report.addRecord(new Record("test", 5, true, 100, 110))
    report.addRecord(new Record("test", 5, true, 100, 200))
    report.addRecord(new Record("test", 5, true, 100, 200))
    report.addRecord(new Record("test", 5, true, 1000, 1600))
    report.addRecord(new Record("test", 5, true, 1050, 1150))
    report.addRecord(new Record("test", 5, false, 1100, 1200))
    report.addRecord(new Record("test", 5, false, 1150, 1250))
    assert(750 == report.maxResponseTime)
  }

  test("median response time") {
    val report = new Report("test", 5)
    report.addRecord(new Record("test", 5, true, 1500, 2000))
    report.addRecord(new Record("test", 5, true, 2000, 2500))
    report.addRecord(new Record("test", 5, true, 2500, 2750))
    report.addRecord(new Record("test", 5, true, 3000, 3750))
    report.addRecord(new Record("test", 5, true, 100, 110))
    report.addRecord(new Record("test", 5, true, 100, 110))
    report.addRecord(new Record("test", 5, true, 100, 200))
    report.addRecord(new Record("test", 5, true, 100, 200))
    report.addRecord(new Record("test", 5, true, 100, 120))
    report.addRecord(new Record("test", 5, true, 1000, 1600))
    report.addRecord(new Record("test", 5, true, 1050, 1150))
    report.addRecord(new Record("test", 5, false, 1100, 1200))
    report.addRecord(new Record("test", 5, false, 1150, 1250))
    assert(100 == report.medianResponseTime)
  }

  test("average response time") {
    val report = new Report("test", 5)
    report.addRecord(new Record("test", 5, true, 1500, 2000))
    report.addRecord(new Record("test", 5, true, 2000, 2500))
    report.addRecord(new Record("test", 5, true, 2500, 2750))
    report.addRecord(new Record("test", 5, true, 3000, 3750))
    report.addRecord(new Record("test", 5, true, 100, 110))
    report.addRecord(new Record("test", 5, true, 100, 110))
    report.addRecord(new Record("test", 5, true, 100, 200))
    report.addRecord(new Record("test", 5, true, 100, 200))
    report.addRecord(new Record("test", 5, true, 100, 120))
    report.addRecord(new Record("test", 5, true, 1000, 1600))
    report.addRecord(new Record("test", 5, true, 1050, 1150))
    report.addRecord(new Record("test", 5, false, 1100, 1200))
    report.addRecord(new Record("test", 5, false, 1150, 1250))
    assert(241 == report.averageResponseTime)
  }

  test("response time percentile") {
    val report = new Report("test", 5)
    report.addRecord(new Record("test", 5, true, 100, 110))
    report.addRecord(new Record("test", 5, true, 100, 120))
    report.addRecord(new Record("test", 5, true, 100, 130))
    report.addRecord(new Record("test", 5, true, 100, 140))
    report.addRecord(new Record("test", 5, true, 100, 150))
    report.addRecord(new Record("test", 5, true, 100, 160))
    report.addRecord(new Record("test", 5, true, 100, 170))
    report.addRecord(new Record("test", 5, true, 100, 180))
    report.addRecord(new Record("test", 5, true, 100, 190))
    report.addRecord(new Record("test", 5, true, 100, 200))
    assert(90 == report.responseTimePercentile(90))
  }

  test("response time percentile double") {
    val report = new Report("test", 5)
    report.addRecord(new Record("test", 5, true, 100, 110))
    report.addRecord(new Record("test", 5, true, 100, 120))
    report.addRecord(new Record("test", 5, true, 100, 130))
    report.addRecord(new Record("test", 5, true, 100, 140))
    report.addRecord(new Record("test", 5, true, 100, 150))
    report.addRecord(new Record("test", 5, true, 100, 160))
    report.addRecord(new Record("test", 5, true, 100, 170))
    report.addRecord(new Record("test", 5, true, 100, 180))
    report.addRecord(new Record("test", 5, true, 100, 190))
    report.addRecord(new Record("test", 5, true, 100, 200))
    assert(90 == report.responseTimePercentile(80.5))
  }

}


object TextReportWriterTest {

  class RecordReaderMock(d: List[Record]) extends RecordReader {
    val it = d.iterator

    override def hasNext: Boolean = it.hasNext
    override def next: Record = it.next
  }

  def getWriterOutput(d: List[Record]): String = {
    val records = new RecordReaderMock(d)
    val out = new ByteArrayOutputStream()
    val writer = new TextReportWriter(records, out)
    writer.write()
    out.toString
  }
}

class TextReportWriterTest extends FunSuite with MockitoSugar {

  import TextReportWriterTest._

  test("emtpy") {
    assert("" == getWriterOutput(List()))
  }

  test("one thread count") {
    assert("""
test:
                  █
   Throughput:    1
                  █
Response Time: 1000
                  ▁
 Failure Rate:    0
 Thread Count:    5
""" == getWriterOutput(List(
      new Record("test", 5, true, 1000, 2000),
      new Record("test", 5, true, 2000, 3000),
      new Record("test", 5, true, 3000, 4000))))
  }

  test("many thread count") {
    val out = getWriterOutput(List(
      new Record("test", 5, true, 1500, 2000),
      new Record("test", 5, true, 2000, 2500),
      new Record("test", 5, true, 2500, 3000),
      new Record("test", 5, true, 3000, 3500),
      new Record("test", 10, true, 100, 200),
      new Record("test", 10, true, 100, 200),
      new Record("test", 10, true, 100, 200),
      new Record("test", 10, true, 100, 200),
      new Record("test", 15, true, 1000, 1100),
      new Record("test", 15, true, 1050, 1150),
      new Record("test", 15, false, 1100, 1200),
      new Record("test", 15, false, 1150, 1250)))
    assert("""
test:
                 ▄   █   █
   Throughput:   2   4   4
                 █   ▂   ▂
Response Time: 500 100 100
                 ▁   ▁   ▄
 Failure Rate:   0   0  50
 Thread Count:   5  10  15
""" == out)
  }

  test("many test methods") {
    assert("""
Test#test1:
                  █
   Throughput:    1
                  █
Response Time: 1000
                  ▁
 Failure Rate:    0
 Thread Count:    5

Test#test2:
                  █
   Throughput:    1
                  █
Response Time: 1000
                  ▁
 Failure Rate:    0
 Thread Count:    5

Test#test3:
                  █
   Throughput:    1
                  █
Response Time: 1000
                  ▁
 Failure Rate:    0
 Thread Count:    5
""" == getWriterOutput(List(
      new Record("Test#test1", 5, true, 1000, 2000),
      new Record("Test#test1", 5, true, 2000, 3000),
      new Record("Test#test1", 5, true, 3000, 4000),
      new Record("Test#test2", 5, true, 1000, 2000),
      new Record("Test#test2", 5, true, 2000, 3000),
      new Record("Test#test2", 5, true, 3000, 4000),
      new Record("Test#test3", 5, true, 1000, 2000),
      new Record("Test#test3", 5, true, 2000, 3000),
      new Record("Test#test3", 5, true, 3000, 4000))))
  }

}

object CsvReportWriterTest {

  class RecordReaderMock(d: List[Record]) extends RecordReader {
    val it = d.iterator

    override def hasNext: Boolean = it.hasNext
    override def next: Record = it.next
  }

  def getWriterOutput(d: List[Record]): String = {
    val records = new RecordReaderMock(d)
    val out = new ByteArrayOutputStream()
    val writer = new SeparatedReportWriter(records, out, ",")
    writer.write()
    out.toString
  }
}

class CsvReportWriterTest extends FunSuite with MockitoSugar {

  import CsvReportWriterTest._

  test("emtpy") {
    assert("name,thread count,throughput,average response time,min response time,10% response time,median response time,90% response time,max response time,failure count,failure rate\n" == getWriterOutput(List()))
  }

  test("one thread count") {
    val out = getWriterOutput(List(
      new Record("test", 5, true, 1000, 2000),
      new Record("test", 5, true, 2000, 3000),
      new Record("test", 5, true, 3000, 4000)))
    assert("""name,thread count,throughput,average response time,min response time,10% response time,median response time,90% response time,max response time,failure count,failure rate
test,5,1,1000,1000,1000,1000,1000,1000,0,0
""" == out)
  }

  test("many thread count") {
    val out = getWriterOutput(List(
      new Record("test", 5, true, 1500, 2000),
      new Record("test", 5, true, 2000, 2500),
      new Record("test", 5, true, 2500, 3000),
      new Record("test", 5, true, 3000, 3500),
      new Record("test", 10, true, 100, 200),
      new Record("test", 10, true, 100, 200),
      new Record("test", 10, true, 100, 200),
      new Record("test", 10, true, 100, 200),
      new Record("test", 15, true, 1000, 1100),
      new Record("test", 15, true, 1050, 1150),
      new Record("test", 15, false, 1100, 1200),
      new Record("test", 15, false, 1150, 1250)))
    assert("""name,thread count,throughput,average response time,min response time,10% response time,median response time,90% response time,max response time,failure count,failure rate
test,5,2,500,500,500,500,500,500,0,0
test,10,4,100,100,100,100,100,100,0,0
test,15,4,100,100,100,100,100,100,2,50
""" == out)
  }

  test("many test methods") {
    assert("""name,thread count,throughput,average response time,min response time,10% response time,median response time,90% response time,max response time,failure count,failure rate
Test#test1,5,1,1000,1000,1000,1000,1000,1000,0,0
Test#test2,5,1,1000,1000,1000,1000,1000,1000,0,0
Test#test3,5,1,1000,1000,1000,1000,1000,1000,0,0
""" == getWriterOutput(List(
      new Record("Test#test1", 5, true, 1000, 2000),
      new Record("Test#test1", 5, true, 2000, 3000),
      new Record("Test#test1", 5, true, 3000, 4000),
      new Record("Test#test2", 5, true, 1000, 2000),
      new Record("Test#test2", 5, true, 2000, 3000),
      new Record("Test#test2", 5, true, 3000, 4000),
      new Record("Test#test3", 5, true, 1000, 2000),
      new Record("Test#test3", 5, true, 2000, 3000),
      new Record("Test#test3", 5, true, 3000, 4000))))
  }

}
