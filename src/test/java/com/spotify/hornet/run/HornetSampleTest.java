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

package com.spotify.hornet.run;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * This test is used within the scala tests.  This is slightly meta, but since we are
 * testing a testing framework, we need a test class which tracks everything that happens to it
 * so we can assert that the right tests we're executed.
 *
 * This is a little weird since some test runners will also run this as a standalone test. The
 * problem is that we want testNothing to fail so we can make sure the framework does the right
 * thing, but that produces a test failure!
 *
 * To avoid this, we are using a failTest flag that is set to false by default.  The test
 * switches on and off to get the right behaviour.
 */
public class HornetSampleTest {

  public static int configCount;
  public static Map config;
  public static int suiteSetupCount;
  public static int suiteTeardownCount;
  public static int setupCount;
  public static int teardownCount;
  public static int testCount;

  public static boolean failTest = false;
  public static boolean exTest = false;
  public static boolean exConfig = false;
  public static boolean exSuiteSetup = false;
  public static boolean exSuiteTeardown = false;
  public static boolean exSetup = false;
  public static boolean exTeardown = false;


  public static void initCounters() {
    configCount = 0;
    config = null;
    suiteSetupCount = 0;
    suiteTeardownCount = 0;
    setupCount = 0;
    teardownCount = 0;
    testCount = 0;
  }

  public static void config(Map<String,String> c) {
    //System.out.println("INIT");
    if (exConfig) throw new RuntimeException("Exception in config");
    configCount += 1;
    config = c;
  }

  @BeforeClass
  public static void suiteSetup() {
    //System.out.println("SUITE SETUP");
    if (exSuiteSetup) {
      //System.out.println("--------------------  Exception in suiteSetup");
      throw new RuntimeException("Exception in suiteSetup");
    }
    suiteSetupCount += 1;
  }

  @AfterClass
  public static void suiteTeardown() {
    //System.out.println("SUITE TEARDOWN");
    if (exSuiteTeardown) {
      //System.out.println("--------------------  Exception in suiteTeardown");
      throw new RuntimeException("Exception in suiteTeardown");
    }
    suiteTeardownCount += 1;
  }

  @Before
  public void setup() {
    //System.out.println("SETUP");
    if (exSetup) throw new RuntimeException("Exception in setup");
    setupCount += 1;
  }

  @After
  public void teardown() {
    //System.out.println("TEARDOWN");
    if (exTeardown) throw new RuntimeException("Exception in teardown");
    teardownCount += 1;
  }

  @Test
  public void testSomething() throws Exception {
    //System.out.println("TEST SOMETHING");
    testCount += 1;
    if (exTest) {
      throw new RuntimeException("Exception in testSomething");
    }
    assertEquals(1, 1);
  }

  @Test
  public void testNothing() throws Exception {
    //System.out.println("TEST NOTHING");
    testCount += 1;
    // failTest is used since some tools will pick up this internal class as a test.
    if (failTest) {
      assertEquals(0, 1);
    }
  }

}
