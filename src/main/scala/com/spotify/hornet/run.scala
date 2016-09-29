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

import java.util.concurrent.{Executors, TimeUnit}

import com.spotify.hornet.log.Logger.log
import com.spotify.hornet.record.{Record, RecordWriter}
import org.junit.runner.{JUnitCore, Request}
import org.junit.runner.notification.{Failure, RunListener, RunNotifier}
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.{FrameworkMethod, InitializationError}

import scala.collection.JavaConversions._
import scala.util.Random

/**
  * Interval companion object which contains some constants and apply method for easy creation.
  */
object Interval {

  private val Ms = "([0-9]+)ms".r
  private val S = "([0-9]+)s".r
  private val M = "([0-9]+)m".r
  private val H = "([0-9]+)h".r

  /**
    * Create a new Interval object and return it.
    */
  def apply(str: String) = new Interval(str)
}

/*
 * The Interval class describes a time span.  It contains a time unit (milliseconds, seconds,
 * minutes, or hours) and a quantity.
 *
 * @param s String representing this time interval (e.g. 100ms, 10s, 5m, 1h).
 */
class Interval(val s: String) {

  val milliseconds = s match {
    case Interval.Ms(v) => v.toLong
    case Interval.S(v) => v.toLong * 1000
    case Interval.M(v) => v.toLong * 1000 * 60
    case Interval.H(v) => v.toLong * 1000 * 60 * 60
    case _ => throw new IllegalArgumentException("'" + s + "' is not a valid interval")
  }
  val seconds = milliseconds / 1000

  override def toString = s
}

/*
 * Class which runs a the load test using the JUnitLoadTestRunner.
 *
 * @param test               Test class to run
 * @param requestsPerSeconds Number of threads for each test cycle.
 * @param poolSize           Size of the thread pool used to run the test.
 * @param cycleDuration      How long each cycle takes.
 * @param cycleSleepDuration Sleep time between cycles.
 * @param recordWriter       Record writer used to write results.
 * @param userConfig         Map of user configuration that will be passed to the test via the config method.
 */
object LoadTestRunner {

  def run(test: Class[_], requestsPerSeconds: List[Int],  poolSize: Int, cycleDuration: Interval,
          cycleSleepDuration: Interval, recordWriter: RecordWriter, userConfig: Map[String, Option[String]]) {
    try {
      // Run each thread cycle sequentially
      for (requestsPerSecond <- requestsPerSeconds) {
        log.info("Starting %s request per second cycle for %s" format (requestsPerSecond, cycleDuration))

        // Create a new JUnit load test runner thread.  This will run the test
        // continuously until the time is up.
        val runner = new JUnitLoadTestTimedRunner(test, requestsPerSecond, poolSize, cycleDuration, recordWriter, userConfig)
        runner.run()

        log.info("Done with %s request per second cycle" format requestsPerSecond)
        // Sleep between cycles.
        try {
          log.info("Sleeping between cycles for " + cycleSleepDuration)
          Thread.sleep(cycleSleepDuration.milliseconds)
        } catch {
          case ex:InterruptedException => log.error("Error while sleeping between cycles", ex)
        }
      }
    } finally {
      // Shutdown writer
      recordWriter.close()
    }
  }

}

/**
  * Class used to run a single cycle of a JUnit based load test.
  *
  * The JUnit test methods will run continuously for the period of time specified.
  *
  * If you have the following JUnit test:
  * <pre>
  * {@code
  * class MyTest {
  *     public static void config(Map<String,String> config) {}
  *
  * @BeforeClass public static void suiteSetup() {}
  * @AfterClass public static void suiteTeardown() {}
  * @Before public void setup() {}
  * @After public void teardown() {}
  * @Test public void test1() {}
  * @Test public void test2() {}
  *       }
  *       }
  *       </pre>
  *
  *       Methods will be executed in the following order:
  *
  *       <pre>
  *       { @code
  *

          }
  * </pre>

  * @param test              The test class to run.  This should be a valid JUnit test.
  * @param requestsPerSecond Number of requests per second to generate.
  * @param poolSize          Size of the thread pool used to run the test.
  * @param cycleDuration     How long each cycle will be.
  * @param recordWriter      Writer used to write test records.
  * @param userConfig        User configuration to pass to the test.
  * @throws InitializationError raised if runner cannot be created.
  *
  */
class JUnitLoadTestTimedRunner(val test: Class[_], val requestsPerSecond: Int,  val poolSize: Int,
                               val cycleDuration: Interval, val recordWriter: RecordWriter,
                               val userConfig: Map[String, Option[String]])
  extends BlockJUnit4ClassRunner(test) {

  if (requestsPerSecond > 1000000) {
    throw new IllegalArgumentException("Currently cannot send more then 1m requests per second")
  }

  val delay = 1000000 / requestsPerSecond
  val schedulerPoolSize = 2
  val workerPoolSize = poolSize

  val junit = new JUnitCore()

  /**
    * Used internally to run tests multiple times.  Should not be called directly.
    */
  def run() {
    log.debug("Starting test " + test)
    try {
      runConfig(test)
      // Start the test using this as the test runner.  This will allow us to overwrite the
      // default run behaviour and run the same test multiple times until the time is done.
      junit.run(Request.runner(this))
    } catch {
      case ex: Throwable => log.error("Exception run test", ex)
    }
  }

  /**
    * This method allows the internal anonymous Runnable to acess super.runChild.
    */
  protected def runSuperChild(method: FrameworkMethod, notifier: RunNotifier) {
    super.runChild(method, notifier)
  }

  override def runChild(method: FrameworkMethod, notifier: RunNotifier) {
    log.debug(s"Starting runner $requestsPerSecond r/s, ${delay}μs delay for $cycleDuration")
    // Add run listener so we can tell when a test failed.
    val listener = new TestRunListener()
    notifier.addListener(listener)
    val scheduler = Executors.newScheduledThreadPool(schedulerPoolSize)
    val pool = Executors.newFixedThreadPool(workerPoolSize)
    scheduler.scheduleAtFixedRate(new Runnable() {
      override def run() {
        pool.execute(new Runnable() {
          override def run() {
            var record: Record = null
            // Remember when the test started.
            val testStart = System.currentTimeMillis
            try {
              log.debug("Starting test " + method.getName)
              // Remove last run state from the listener
              listener.success = true
              // Run the test
              runSuperChild(method, notifier)
              // Create record
              record = Record(getTestName(test, method), requestsPerSecond, listener.success, testStart,
                              System.currentTimeMillis)
            } catch {
              case ex: Throwable => {
                log.error("Got error running test " + method, ex)
                // If we got an error, the test failed.
                record = Record(test.getCanonicalName(), requestsPerSecond, false, testStart,
                                System.currentTimeMillis)
              }
            } finally {
              try {
                // Record the test run.
                log.debug("Writing record: " + record)
                recordWriter.write(record)
              } catch {
                case ex: InterruptedException => log.error("Got error while adding new record", ex)
              }
            }
          }
        })
      }
    }, Random.nextInt(delay), delay, TimeUnit.MICROSECONDS)

    try {
      // Waiting for test to finish
      Thread.sleep(cycleDuration.milliseconds)
    } catch {
      case ex: InterruptedException => log.error("Got error while sleeping", ex)
    }
    try {
      log.debug("Shutting down cycle")
      scheduler.shutdown()
      pool.shutdown()
      scheduler.awaitTermination(delay * 2, TimeUnit.MILLISECONDS)
      pool.awaitTermination(10, TimeUnit.MINUTES)
    } catch {
      case ex: InterruptedException => log
        .error("Got error while awaiting scheduler termination", ex)
    }
    log.debug("Runner shut down")
  }

  private def runConfig(test: Class[_]) = {
    try {
      // Get static method
      val m = test.getMethod("config", Class.forName("java.util.Map"))
      // Call it
      log.debug("Initializing test config: " + userConfig.mkString(", "))
      m.invoke(null, mapAsJavaMap(userConfig.map { e => (e._1 -> e._2.getOrElse(null)) }))
    } catch {
      case ex: NoSuchMethodException => log.debug("Config method not defined (this is fine)")
    }
  }

  /**
    * Get the test name based on the test class and method name.
    *
    * @param test   Test class.
    * @param method Test method
    * @return Test name
    */
  def getTestName(test: Class[_], method: FrameworkMethod) = test.getSimpleName + '#' +
                                                             method.getName
}

/**
  * Run listener used to determine if the test ran successfully or failed.
  */
class TestRunListener extends RunListener {

  var success = true

  /**
    * Gets called on test failure
    */
  override def testFailure(failure: Failure) {
    super.testFailure(failure)
    log.error(failure.getDescription.toString, failure.getException)
    success = false
  }

}
