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

package com.spotify.hornet.record


import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Reader
import java.io.Writer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.Props
import akka.pattern.gracefulStop
import scala.concurrent.Await

import com.spotify.hornet.log.Logger.log


/**
 * The Record class holds data for a single test run.
 *
 * @param name        The name of the test.
 * @param threadCount Concurrent thread count.
 * @param successful  Did the test complete successfully.
 * @param start       Test start time in milliseconds.
 * @param end         Test end time in milliseconds.
 */
case class Record(val name: String,
  val threadCount: Int,
  val successful: Boolean,
  val start: Long,
  val end: Long) {
  val duration:Long = end - start
}

/**
 * Trait for reading Record objects and allowing clients to iterate over them.
 */
trait RecordReader extends Iterator[Record]

/**
 * Companion object which holds some constants.
 */
object CsvRecordReader {

  /**
   * Record field separator.
   */
  val Separator = ","
}

/**
 * Class used for reading Record objects from a file in CSV format.
 *
 * @param in Reader from which records will be read.
 */
class CsvRecordReader(in: Reader) extends RecordReader {
  val reader = new BufferedReader(in)
  // Get rid of the header row.
  reader.readLine()
  // Get the first record.
  var nextLine = read()

  def hasNext: Boolean = nextLine.nonEmpty
  def next: Record = try {
    // Return the next record.
    Record(
      name        = nextLine.get(0),
      threadCount = nextLine.get(1).toInt,
      successful  = nextLine.get(2).toBoolean,
      start       = nextLine.get(3).toLong,
      end         = nextLine.get(4).toLong)
  } finally {
    // Read next record.  (This is a little weird use of a finally block,
    // but it make the code nicer).
    nextLine = read()
  }

  /*
   * Read the next line from the supplied Reader and split.
   */
  private def read(): Option[Array[String]] = {
    val line = reader.readLine()
    if (line != null) {
      Some(line.split(CsvRecordReader.Separator))
    } else {
      None
    }
  }

}

/**
 * Trait for writing Record objects to persistence medium from multiple threads.
 */
trait RecordWriter {

  /**
   * Write a record object or schedule for writing.
   *
   * @param r The record to write.
   * @throws InterruptedException is raised if the record could not be queued for writing.
   */
  def write(r: Record)

  /**
   * Shutdown the writer.  No more records can be written.
   */
  def close()
}

/**
 * Companion object which holds some constants.
 */
object CsvRecordWriter {
  val ReporterCapacity = 1000;
  val PollTimeout = 100L;
}

/**
 * Class used for writing Record objects in CSV format to a file.
 *
 * @param out Writer to which records would be written.
 */
class CsvRecordWriter(out: Writer) extends RecordWriter {
  // Create actor system which is used to create actors.
  val actorSystem = ActorSystem("hornetSystem")
  // Create the writer actor
  val actor = actorSystem.actorOf(Props(new WriterActor(out)), name="csvRecordWriter")

  /**
   * Write out a record in CSV format.
   */
  def write(r: Record) {
    log.debug("Adding record to queue " + r)
    actor ! r
  }

  def close {
    log.debug("Shutting down CsvRecordWriter");
    // Shutdown writer
    actorSystem.shutdown
    // Wait for it to terminate
    actorSystem.awaitTermination
  }

  /*
   * The WriterActor writes out messages asynchronously.
   *
   * Since writing to files in Java is not thread-safe, and multiple threads need to write results
   * to the same file, we need a queue to achieve this.
   */
  class WriterActor(out: Writer) extends Actor {
    // Create buffered writer for better performance
    val writer = new BufferedWriter(out)
    // Write out header (this will happen when the first message is received)
    write(List("name", "thread_count", "successful", "start", "end", "duration"))

    /*
     * Write out a list of strings as a Separator separated line.
     */
    private def write(s: List[String]) {
      out.write(s.mkString(CsvRecordReader.Separator))
      out.write('\n')
      out.flush
    }

    def receive = {
      case r:Record => {
        log.debug("Writing record " + r);
        // Write to file in CSV format
        write(List(r.name,
          r.threadCount.toString,
          r.successful.toString,
          r.start.toString,
          r.end.toString,
          r.duration.toString))
      }
    }
  }

}
