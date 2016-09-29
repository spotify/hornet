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

import java.util.Date

import com.spotify.hornet.record.Record
import com.spotify.hornet.record.RecordReader
import com.spotify.hornet.log.Logger

import scala.collection.immutable.TreeSet
import scala.collection.immutable.TreeMap
import scala.collection.immutable.SortedSet
import scala.collection.immutable.SortedMap
//import scala.collection.immutable.Iterable

import scala.collection.mutable.{TreeSet => MutableTreeSet}
import scala.collection.mutable.{SortedSet => MutableSortedSet}

import java.io.OutputStream
import java.text.DecimalFormat

import scala.collection.mutable.ListBuffer
import scala.io.Source

import scala.math

class Report(val name: String, val threadCount: Int) {
  var totalDuration: Long = 0
  var recordCount: Int = 0
  var failureCount: Int = 0
  var start: Long =  Long.MaxValue
  var end: Long = Long.MinValue

  var responseTimeBins: SortedMap[Long, Long] = new TreeMap[Long, Long]

  /**
    * Aggregate a new record.
    *
    * @param r Record to add.
    */
  def addRecord(r: Record) {
    // Make sure name and thread count match
    require(name == r.name, "Record name must match report name")
    require(threadCount == r.threadCount, "Record thread count must match report thread count")

    // Save total duration and count so we can calculate average response time.
    val curDuration = r.duration
    totalDuration += curDuration
    // Store Response Time so we can calculate percentiles)
    val responseTimeBin = responseTimeBins.get(curDuration)
    if (responseTimeBin.isEmpty) {
      responseTimeBins = responseTimeBins + (curDuration -> 1)
    } else {
      responseTimeBins = responseTimeBins + (curDuration -> (responseTimeBin.get + 1))
    }
    recordCount += 1
    // Save overall start and end time do we can calculate throughput.
    start = Math.min(start, r.start)
    end = Math.max(end, r.end)
    if (!r.successful) {
      failureCount += 1
    }
  }

  /**
    * @return Get average time it took to run a test at this thread count in milliseconds.
    */
  def averageResponseTime: Long = totalDuration / recordCount

  /**
    * @return Get median of time it took to run a test at this thread count in milliseconds.
    *
    * NOTE:  This is not 100% accurate for small sample sizes.  Does not take into account averaging
    * when there are an even number of data points
    */
  def medianResponseTime: Long = responseTimePercentile(50)

  /**
    * @param percentile Integer percentile to get, should be between 0 and 100
    * @return Get the specified percentile of time it took to run a test at this thread count in
    *         milliseconds.
    */
  def responseTimePercentile(percentile: Double): Long = {
    require(percentile >= 0)
    require(percentile <= 100)
    var currentCount: Long = 0
    var ret: Long = 0
    for (e <- responseTimeBins) {
      // Count how many values encountered thus far
      currentCount += e._2
      // If you have counted past the percentile desired of all entries, return the value
      if (currentCount >= recordCount / 100.0 * percentile) {
        return e._1
      }
    }
    return 0
  }

  /**
    * @return Get minimum of time it took to run a test at this thread count in milliseconds.
    */
  def minResponseTime = responseTimeBins.firstKey

  /**
    * @return Get maximum of time it took to run a test at this thread count in milliseconds.
    */
  def maxResponseTime = responseTimeBins.lastKey

  /**
    * @return Throughput for this thread count in number of tests per second.
    */
  def throughput: Int = {
    // Get the total duration of the test in seconds.
    var diff = (end - start) / 1000
    // Round up to 1 sec.
    if (diff < 1) {
      diff = 1
    }
    // Calculate throughput.
      (recordCount / diff).toInt
  }

  /*
   * Precentage of failured tests.
   */
  def failureRate:Int = (failureCount.toFloat / recordCount.toFloat * 100.0).toInt

}


/**
  * A ReportGroup contains all {@link Report}s which have the same name.
  *
  * This makes it easier to print all of them together when generating a report.
  */
class ReportGroup(val name: String) extends Iterable[Report] with Ordered[ReportGroup] {

  var reports = new TreeMap[Int, Report]()

  def iterator = reports.values.iterator
  def compare(that: ReportGroup) = name.compare(that.name)

  /**
    * Add another record to this report.
    *
    * @param record {@link Record} the add
    */
  def addRecord(record: Record) {
    // Get the right report by thread count
    var report = reports.get(record.threadCount)
    if (report.isEmpty) {
      // Create a new report if one does not already exist.
      report = Some(new Report(record.name, record.threadCount))
      reports = reports + (record.threadCount -> report.get)
    }
    report.get.addRecord(record)
  }

  override def toString = name

}


/**
  * Interface for writing a report.
  */
trait ReportWriter {

  /**
    * Write report.
    *
    * @throws IOException is raised of the report could not be written.
    */
  def write()
}


/**
  * Abstract class which provides record aggregating functionality that can be used by subclasses.
  */
abstract class AbstractReportWriter(records: RecordReader) extends ReportWriter {

  /**
    * Get all reports grouped by name.
    *
    * @return Reports aggregated grouped thread count.
    */
  protected def getReports(): SortedSet[ReportGroup] = {
    var all = new TreeMap[String, ReportGroup]()
    for (record <- records) {
      // Get the right report group
      var reports = all.get(record.name)
      // Create it if it does not already exist
      if (reports.isEmpty) {
        reports = Some(new ReportGroup(record.name))
        all = all + (reports.get.name -> reports.get)
      }
      reports.get.addRecord(record);
    }
    return TreeSet[ReportGroup]() ++ all.values.toSet
  }
}

object TextReportWriter {

  val Ticks = Array('▁', '▂', '▃', '▄', '▅', '▆', '▇', '█')

  /**
    * Get the right tick mark based on the value relative to min and max.
    *
    * @param value Value to find the tick for.
    * @param min   Min for this value.
    * @param max   Max for this value.
    * @return Tick character.
    */
  def getTick(value: Double, min: Double, max: Double) = {
    val tick = (value - min) / (max - min) * (Ticks.length - 1)
    Ticks(tick.toInt)
  }

}

/**
  * Plain text report writer.
  */
class TextReportWriter(records: RecordReader, out: OutputStream) extends AbstractReportWriter(records) {

  import TextReportWriter._

  override def write {
    val groups = getReports()
    // Write out each group
    for (group <- groups) {
      out.write('\n')
      out.write(group.name.getBytes)
      out.write(":\n".getBytes)
      writeReport(group)
    }
  }

  private def writeReport(group: ReportGroup) {
    // Find min/max throughput and response time.
    var minThroughput = Int.MaxValue
    var maxThroughput = Int.MinValue
    var minResponseTime = Long.MaxValue
    var maxResponseTime = Long.MinValue
    var maxThreadCount = Int.MinValue
    val maxFailureRate = 100 // This is always 100%
    for (report <- group) {
      minThroughput = Math.min(minThroughput, report.throughput)
      maxThroughput = Math.max(maxThroughput, report.throughput)
      minResponseTime = Math.min(minResponseTime, report.averageResponseTime)
      maxResponseTime = Math.max(maxResponseTime, report.averageResponseTime)
      maxThreadCount = Math.max(maxThreadCount, report.threadCount)
    }
    // Figure out the width of a cell.
    val width = getWidth(maxThroughput, maxResponseTime, maxThreadCount, maxFailureRate)
    // Write throughput
    out.write("              ".getBytes)
    for (report <- group) {
      write(width, getTick(report.throughput, 0, maxThroughput))
    }
    out.write('\n')
    out.write("   Throughput:".getBytes)
    for (report <- group) {
      write(width, report.throughput)
    }
    out.write('\n')
    // Write response time
    out.write("              ".getBytes)
    for (report <- group) {
      write(width, getTick(report.averageResponseTime, 0, maxResponseTime))
    }
    out.write('\n')
    out.write("Response Time:".getBytes)
    for (report <- group) {
      write(width, report.averageResponseTime)
    }
    out.write('\n')
    // Write failure rate
    out.write("              ".getBytes)
    for (report <- group) {
      write(width, getTick(report.failureRate, 0, maxFailureRate))
    }
    out.write('\n')
    out.write(" Failure Rate:".getBytes)
    for (report <- group) {
      write(width, report.failureRate)
    }
    out.write('\n')
    // Write thread count
    out.write(" Thread Count:".getBytes)
    for (report <- group) {
      write(width, report.threadCount)
    }
    out.write('\n')
    out.flush()
  }

  /**
    * Write out a cells value with proper padding.
    *
    * @param width The cell's width.
    * @param v     The cell's value.
    * @throws IOException is raise if value cannot be written.
    */
  private def write(width: Int, v: AnyVal) {
    write(width, v.toString)
  }

  /**
    * Write out a cells value with proper padding.
    *
    * @param width The cell's width.
    * @param s     The cell's value.
    * @throws IOException is raise if value cannot be written.
    */
  private def write(width: Int, s: String) {
    out.write(String.format("%" + width + "s", s).getBytes)
  }

  /**
    * Calculate the cell's width.  This is the max value length as a String plus 1 for padding.
    *
    * @param values The values to consider.
    * @return The cell's width.
    */
  private def getWidth(values: Long*): Int = {
    var width = Int.MinValue
    for (value <- values) {
      width = math.max(width, String.valueOf(value).length())
    }
    width + 1
  }

}


/**
  * Structured report writer.
  *
  * Pass in the seperator to create CSV or TSV or whatever.
  */
class SeparatedReportWriter(records: RecordReader, out: OutputStream, sep: String) extends AbstractReportWriter(records) {

  override def write {
    // Write CSV header
    out.write(List("name", "thread count", "throughput", "average response time",
      "min response time", "10% response time", "median response time", "90% response time", "max response time",
      "failure count", "failure rate").mkString(sep).getBytes)
    out.write('\n')
    val groups = getReports
    // Write out each group
    for (group <- groups) {
      for (report <- group) {
        val buff = new ListBuffer[Any]
        buff += group.name
        buff += report.threadCount
        buff += report.throughput
        buff += report.averageResponseTime
        buff += report.minResponseTime
        buff += report.responseTimePercentile(25)
        buff += report.medianResponseTime
        buff += report.responseTimePercentile(75)
        buff += report.maxResponseTime
        buff += report.failureCount
        buff += report.failureRate
        val line = buff.mkString(sep)
        out.write(line.getBytes)
        out.write('\n')
      }
    }
  }

}

/**
  * HTML report writer.
  */
class HtmlReportWriter(records: RecordReader, out: OutputStream) extends AbstractReportWriter(records) {

  val df = new DecimalFormat("#.###")

  private def toJsonArray(it: Iterable[Any]) = "[" + it.mkString(",") + "]"
  private def calculatePercentiles(records: Array[Record]): SortedMap[Double, Long] = {
    var percentiles: SortedMap[Double, Long] = new TreeMap[Double, Long]
    var l = records.length
    for (p <- 0.001 until 1 by 0.001) {
      val i: Int = (p * l).toInt
      val d = records(i).duration
      percentiles += ((p * 100) -> d)
    }
    return percentiles
  }

  override def write {
    val groups = getReports
    val buff = new ListBuffer[Any]
    for (group <- groups) {
      val name = group.name
      val date = new Date
      val threadCount = toJsonArray(group.map(_.threadCount))
      val requests = toJsonArray(group.map(_.recordCount))
      val failureRate = toJsonArray(group.map(_.failureRate))
      val failures = toJsonArray(group.map(_.failureCount))
      val throughput = toJsonArray(group.map(_.throughput))
      val responseTime = "[" + group.map(g =>
        "[" + List(g.responseTimePercentile(0),
                   g.responseTimePercentile(25),
                   g.responseTimePercentile(50),
                   g.responseTimePercentile(75),
                   g.responseTimePercentile(100)).mkString(",") + "]"
      ).mkString(",\n") + "]"
      val percentile = toJsonArray(for (p <- 0.001 until 1 by 0.001) yield df.format(p))
      val duration = toJsonArray(for (r <- group) yield toJsonArray(for (p <- 0.001 until 1 by 0.001) yield r.responseTimePercentile(p*100)))
      buff += s"""
   { name: '$name',
     date: '$date',
     threadCount: $threadCount,
     requests: $requests,
     failureRate: $failureRate,
     failures: $failures,
     throughput:  $throughput,
     responseTime: $responseTime,
     percentile: $percentile,
     duration: $duration
   }"""
    }
    val template = getClass.getResourceAsStream("/report.html")
    var fillin = false
    for (line <- Source.fromInputStream(template).getLines) {
      if (line.contains("<!-- start data -->")) {
        fillin = true
      } else if (fillin == true && line.contains("<!-- end data -->")) {
        out.write("var data = [\n".getBytes)
        out.write(buff.mkString(",\n").getBytes())
        out.write("]\n".getBytes)
        fillin = false
      } else if (fillin == false) {
        out.write(line.getBytes)
        out.write('\n')
      }
    }
  }

}
