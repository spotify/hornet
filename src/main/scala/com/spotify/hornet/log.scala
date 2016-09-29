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

package com.spotify.hornet.log

/**
 * Class used for simple System.out/err logging.
 *
 * Implementing this instead of using a more standard logging library so that we don't have that
 * dependency that will surely collide with whatever the user application is doing.
 */

import java.text.SimpleDateFormat
import java.util.Date

object Logger {
  var verbose = false;
  var log = new Logger
}

class Logger {

  val df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS")

  /**
   * Return current logger.
   *
   * Makes code a little more readable (log.debug vs. just debug) and allows tests to overwrite.
   */
  var log = this

  /**
   * Log a debug statement to stdout.
   *
   * This method signature and format follows {@link String#format(String, Object...)}.
   */
  def debug(msg: String) {
    if (Logger.verbose) {
      //print(date);
      println(msg);
    }
  }

  /**
   * Log an info statement to stdout.
   *
   * This method signature and format follows {@link String#format(String, Object...)}.
   */
  def info(msg: String) {
    print(date);
    println(msg);
  }

  /**
   * Log an exception to stderr.
   */
  def error(msg: String, ex: Throwable) {
    //Console.err.print(date);
    Console.err.println(msg);
    ex.printStackTrace(Console.err);
  }

  /**
   * Log an exception to stderr.
   */
  def error(msg: String) {
    //Console.err.print(date);
    Console.err.println(msg);
  }


  /**
   * Get current date formatted as a String
   */
  private def date:String = "[" + df.format(new Date()) + "] "

}
