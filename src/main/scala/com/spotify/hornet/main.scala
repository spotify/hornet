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

package com.spotify.hornet.main

import java.io.ByteArrayOutputStream
import java.util.Properties
import java.io.{BufferedWriter, BufferedOutputStream, BufferedReader}
import java.io.{File, FileOutputStream, FileReader, FileWriter}

import com.spotify.hornet.log.Logger
import com.spotify.hornet.log.Logger.log
import com.spotify.hornet.record.CsvRecordWriter
import com.spotify.hornet.record.CsvRecordReader
import com.spotify.hornet.run.Interval
import com.spotify.hornet.run.LoadTestRunner
import com.spotify.hornet.config.ConfigReader
import com.spotify.hornet.config.ExitException
import com.spotify.hornet.config.Options
import com.spotify.hornet.report.TextReportWriter
import com.spotify.hornet.report.SeparatedReportWriter
import com.spotify.hornet.report.HtmlReportWriter
import com.spotify.hornet.config.RunType._

/**
 * Load test runner
 */
object Hornet extends Logger {

  val Error = 2
  val Ok = 0

  /**
   * Application entry point.
   *
   * @param args command line arguments.
   */
  def main(args: Array[String]) {
    try {
      val opts = ConfigReader.load(args)
      // Setup logging
      Logger.verbose = opts.verbose

      log.debug(opts.toString)

      opts.runType match {
        ///////////
        // TEST //
        ///////////
        case Test => {
          log.info("Running load test, recording results in %s" format (opts.recordFile))
          // Create record writer.
          val recordOut = new BufferedWriter(new FileWriter(opts.recordFile, false))
          // Create record writer.
          val recordWriter = new CsvRecordWriter(recordOut)
          // Create load test runner

          try {
            try {
              // Run tests (this might take awhile)
              LoadTestRunner.run(opts.test,
                                 opts.requestsPerSeconds,
                                 opts.poolSize,
                                 opts.cycleDuration,
                                 opts.cycleSleepDuration,
                                 recordWriter,
                                 opts.userConfig)
            } finally {
              // Close record writer.
              recordOut.close()
            }
          } finally {
            recordWriter.close()
            recordOut.close()
          }
        }
        ////////////
        // REPORT //
        ////////////
        case Report => {
          // Text report
          printTextReport(opts)
          // CSV report
          if (opts.csvReport) {
            writeCsvReport(opts)
          }
          if (opts.htmlReport) {
            writeHtmlReport(opts)
          }
        }
      }
      sys.exit(Ok)

    }
    catch {
      case ex: IllegalArgumentException => {
        log.error(ex.getMessage)
        sys.exit(Error)
      }
      case ex: ExitException => {
        log.error(ex.message)
        sys.exit(ex.code)
      }
    }
  }

  def printTextReport(opts: Options) = {
    val in = new BufferedReader(new FileReader(opts.recordFile))
    val buff = new ByteArrayOutputStream()
    try {
      val writer = new TextReportWriter(new CsvRecordReader(in), new BufferedOutputStream(buff))
      writer.write()
      log.info(buff.toString)
    } finally {
      buff.close()
      in.close()
    }
  }

  def writeCsvReport(opts: Options) = {
    val in = new BufferedReader(new FileReader(opts.recordFile))
    val out = new FileOutputStream(opts.csvReportFile, false)
    val writer = new SeparatedReportWriter(new CsvRecordReader(in), out, ",")
    log.info("Writing CVS report %s" format (opts.csvReportFile))
    try {
      writer.write()
    } finally {
      out.close()
      in.close()
    }
  }

  def writeHtmlReport(opts: Options) = {
    val in = new BufferedReader(new FileReader(opts.recordFile))
    val out = new FileOutputStream(opts.htmlReportFile, false)
    val writer = new HtmlReportWriter(new CsvRecordReader(in), out)
    log.info("Writing HTML report %s" format (opts.htmlReportFile))
    try {
      writer.write()
    } finally {
      out.close()
      in.close()
    }
  }
}
