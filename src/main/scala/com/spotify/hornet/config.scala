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
import java.util.Date
import java.util.Properties
import java.io.FileReader
import java.text.SimpleDateFormat
import java.text.ParsePosition

import scala.collection.mutable.MutableList
import scala.util.matching.Regex
import scala.collection.JavaConverters._

import com.spotify.hornet.run.Interval

class ExitException(val code: Int, val message: String) extends RuntimeException(message)

object RunType extends Enumeration {
  type RunType = Value
  val Test = Value("test")
  val Report = Value("report")
}
import RunType._

object RegexUtils {
  class MatchableRegex(r: Regex) {
    def matches(s: String) = r.pattern.matcher(s).matches
  }
  implicit def regextToMatchableRegex(r: Regex) = new MatchableRegex(r)
}

/***
 * Class used for storing command line options.
 */
class Options() {

  private val hornetLog = """^hornet_record.*\.log$""".r
  private val year = """\d\d\d\d""".r
  private val df = new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss")

  private var _recordFile: File     = null
  private var _textReportFile: File = null
  private var _csvReportFile: File  = null
  private var _htmlReportFile: File = null

  // Common options
  var runType: RunType              = null
  var verbose                       = false

  // Test options
  var test: Class[_]                = null
  var cycleDuration: Interval       = null
  var cycleSleepDuration: Interval  = null
  var requestsPerSeconds: List[Int] = Nil
  var poolSize: Int                 = 0
  var recordDir: File               = null

  // Report options
  var csvReport                     = false
  var htmlReport                    = false

  // User defined configuration
  var userConfig: Map[String, Option[String]] = null


  /***
   * If reportFile is not null, this function will just return it.  Otherwise it will try to
   * generate a new reportFile name based on the recordFile and the extension provided.
   */
  private def getReportFile(reportFile: File, extension: String) = {
    if (recordFile != null && reportFile == null) {
      new File(recordFile.getParent, "hornet_report_" + df.format(reportDate) + "." + extension)
    } else {
      reportFile
    }
  }

  def recordFile_=(value: File): Unit = _recordFile = value

  /***
   * Get or generate a recordFile name.
   *
   * If the user privded a recordFile explicitly, just use that.  Otherwise, generate a recordFile
   * based on the recordDir.  If this is a load test, just generate a new file based on the current
   * date.  If this is a report, then try to find the lastest recordFile and generate a name to
   * match.
   */
  def recordFile = {
    if (recordDir != null && _recordFile == null) {
      runType match {
        // For test generate a fresh record file based in the data.
        case Test => new File(recordDir, "hornet_record_" + df.format(new Date) + ".log")
        case Report => {
          // For reports return last modified .log file
          val recordLogs = recordDir.listFiles.filter(f => hornetLog.findFirstIn(f.getName).isDefined)
          if (recordLogs.isEmpty) {
            // User probably used the wrong record dir or forgot to specify the record file
            throw new NullPointerException(s"No record files found in $recordDir, please adjust --record-file or --record-dir")
          } else {
            // If there are more the one, get the lastest record file.
            recordLogs.sortBy(-_.lastModified).head
          }
        }
        case _ => _recordFile
      }
    } else {
      _recordFile
    }
  }

  /***
   * Try and get the date from the recordFile name.  If we cannot, just return a new date.
   */
  def reportDate = try {
    val rf = recordFile.toString
    val d = df.parse(rf, new ParsePosition(year.findFirstMatchIn(rf).get.start))
    // Unfortunately SimpleDateFormat.parse can return null on error instead of throwing an
    // exception.  So, we have to check that separately.
    if (d != null) d else new Date
  } catch {
    case ex @ (_: NullPointerException | _: StringIndexOutOfBoundsException | _: java.util.NoSuchElementException) => new Date
  }

  def textReportFile_=(value: File): Unit = _textReportFile = value
  def textReportFile = getReportFile(_textReportFile, "txt")

  def csvReportFile_=(value: File): Unit = _csvReportFile = value
  def csvReportFile = getReportFile(_csvReportFile, "csv")

  def htmlReportFile_=(value: File): Unit = _htmlReportFile = value
  def htmlReportFile = getReportFile(_htmlReportFile, "html")

  override def toString = {
    var b = new StringBuilder
    b ++= "runType: " + runType
    b ++= "\ntest: " + test
    b ++= "\ncycleDuration: " + cycleDuration
    b ++= "\ncycleSleepDuration: " + cycleSleepDuration
    b ++= "\nrequestsPerSeconds: " + requestsPerSeconds.mkString(":")
    b ++= "\nrecordFile: " + recordFile
    b ++= "\ntextReportFile: " + textReportFile
    b ++= "\ncsvReportFile: " + csvReportFile
    b ++= "\nhtmlReportFile: " + htmlReportFile
    b ++= "\nverbose: " + verbose
    b ++= userConfig.foldLeft("\nuserConfig:"){ (acc, kv) =>  acc + "\n  " + kv._1 + ": " + kv._2.getOrElse("") }
    b.toString
  }
}

object ConfigReader {

  import RegexUtils._

  type Config = Map[String, Option[String]]

  private val Switch = "--(\\S+)".r

  val USAGE = """Usage: hornet <type> [OPTIONS]

Type:
  test   Run a load test.
  report Generate a report based on the previous load test (test).

Common Options:
  --test <CLASS>              The test class to run.  This should be
                              a valid JUnit test.
  --cycle-duration <INTERVAL> How long each cycle will be.
  --cycle-sleep-duration <INTERVAL> Sleep time between cycles
  --requests-per-seconds <COUNTS> Number of requests per second to
                              run each cycle
  --pool-size <INT>           Size of the test worker pool.  This should be
                              large enough to accomodate r/s load.
  --record-file <FILE>        File to write records to.  File will be
                              overwritten if exists.
  --record-dir <DIR>          Dir to write/read records to/from.
  --html-report               Generate a HTML report
  --html-report-file <FILE>   File to write HTML report to.
  --csv-report                Generate a CSV report
  --csv-report-file <FILE>    File to write CSV report to.
  --config <FILE>             Configuration file.
  --verbose                   Write logs to stdout.

  CLASS     Full class name including package.
  INTERVAL  Time interval with unit (e.g. 100ms, 10s, 3m, 1h).
  FILE      File path.
  FILE      Dir path.
  INT       Integer
  COUNTS    Colon seperated list of ints (e.g. 1:2:3)
            or dash seperated range <START-STEP-END> (e.g. 1-1-3)

"""

  def parseParams(args: List[String]): Config = args match {
    case Nil                                              => Map[String, Option[String]]()
    case Switch(name) :: Nil                              => Map(name -> None)
    case Switch(name) :: tail if Switch matches tail.head => Map(name -> None) ++ parseParams(tail)
    case Switch(name) :: value :: tail                    => Map(name -> Some(value)) ++ parseParams(tail)
    case runType :: Nil                                   => Map("type" -> Some(runType))
    case runType :: tail                                  => Map("type" -> Some(runType)) ++ parseParams(tail)
    case tail                                             => throw new ExitException(2, "Invalid parameter '" + tail.mkString(" ") + "' (see --help for more details)")
  }

  def parseProperties(p: Map[String, String]): Config = p.map{
    case (k, "") => (k.replace("_", "-"), None)
    case (k, v)  => (k.replace("_", "-"), Some(v))
  }

  def merge(c1: Config, c2: Config): Config = c2.map{ case (k, v) => k -> c1.getOrElse(k, v) } ++ c1

  /***
   * Validate required options.
   *
   * @throws IllegalArgumentException if any of the required parameters is missing
   */
  def validateOptions(o: Options) {
    if (o.runType == null) { throw new IllegalArgumentException("Must specify run type (one of test or report)") }
    val errors = MutableList[String]()
    o.runType match {
      case Test => {
        if (o.test == null) { errors += "Must specify --test" }
        if (o.cycleDuration == null) { errors += "Must specify --cycle-duration" }
        if (o.cycleSleepDuration == null) { errors += "Must specify --cycle-sleep-duration" }
        if (o.requestsPerSeconds == Nil) { errors += "Must specify --requests-per-seconds" }
        if (o.poolSize == 0) { errors += "Must specify --pool-size" }
        if (o.recordDir == null && o.recordFile == null) { errors += "Must specify --record-dir or --record-file" }
      }
      case Report => {
        if (o.recordDir == null && o.recordFile == null) { errors += "Must specify --record-dir or --record-file" }
      }
    }
    if (!errors.isEmpty) {
      throw new IllegalArgumentException(errors.mkString("\n"))
    }
  }

  /***
   * Parse thread count string of the form "1:2:3".
   *
   * @return List of ints for each thread count.
   */
  def parseRequestsPerSeconds(s: String): List[Int] = if (s.contains("-")) {
    val parts = s.split("-").filter(_.nonEmpty).map(_.toInt).toList
    if (parts.length != 3) throw new IllegalArgumentException("Ranges should have three ints seperated by dashes 'START-STEP-END'")
    (parts(0) to parts(2) by parts(1)).toList
  } else {
    s.split(":").filter(_.nonEmpty).map(_.toInt).toList
  }

  /**
   * Parse run type (can be "test" or "report").
   *
   * Throw Illegalargumentexception if no match.
   */
  def parseRunType(s: String): RunType = try {
    RunType.withName(s)
  } catch {
    case ex: NoSuchElementException => throw new IllegalArgumentException("Must specify run type (one of test or report)")
  }

  def parseOptions(c: Config): Options = {
    val options = new Options()
    var userConfig = Map[String, Option[String]]()
    c.map {
      case ("type", Some(value))                 => options.runType = parseRunType(value)
      case ("test", Some(value))                 => options.test = Class.forName(value)
      case ("cycle-duration", Some(value))       => options.cycleDuration = new Interval(value)
      case ("cycle-sleep-duration", Some(value)) => options.cycleSleepDuration = new Interval(value)
      case ("requests-per-seconds", Some(value)) => options.requestsPerSeconds = parseRequestsPerSeconds(value)
      case ("pool-size", Some(value))            => options.poolSize = value.toInt
      case ("record-dir", Some(value))           => options.recordDir = new File(value)
      case ("record-file", Some(value))          => options.recordFile = new File(value)
      case ("text-report-file", Some(value))     => options.textReportFile = new File(value)
      case ("csv-report-file", Some(value))      => options.csvReportFile = new File(value)
      case ("csv-report", None)                  => options.csvReport = true
      case ("html-report-file", Some(value))     => options.htmlReportFile = new File(value)
      case ("html-report", None)                 => options.htmlReport = true
      case ("verbose", None)                     => options.verbose = true
      case ("verbose", Some(value))              => options.verbose = value.toBoolean
      case ("help", None)                        => throw new ExitException(1, USAGE)
      case (option, value)                       => userConfig += (option -> value)
    }
    options.userConfig = userConfig
    validateOptions(options)
    options
  }

  /***
   * Load configuration from file if filename is specified.
   *
   * @param fn: Filename returned from Config.  This maybe None
   */
  def loadFile(fn: Option[Option[String]]): Map[String, String] = {
    if (!fn.isEmpty && !fn.get.isEmpty) {
      val p = new Properties
      p.load(new FileReader(fn.get.get))
      p.asScala.toMap
    } else {
      Map()
    }
  }

  /***
   * Load parse and load configuration from command line params and config file.
   *
   * Returns raw config
   */
  def loadConfig(args: Array[String]) = {
    // Parse command line arguments
    val argsConfig = parseParams(args.toList)
    // If config is defined, load configuration from file
    val fileConfig = parseProperties(loadFile(argsConfig.get("config")))
    // Merge command line and file config (Make sure not to pass in config)
    merge(argsConfig.filter(_._1 != "config"), fileConfig)
  }

  /***
   * Parse, load, and validate configuration from command line params and config file for loadtest
   * and report run.
   */
  def load(args: Array[String]) = {
    // Parse and validate options
    val config = loadConfig(args)
    //println(config)
    parseOptions(config)
  }
}
