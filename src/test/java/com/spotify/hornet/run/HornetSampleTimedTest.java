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

import org.junit.Test;

import static org.junit.Assert.assertEquals;


/**
 * This test is similar to SampleHornetTest.  We cannot reuse SampleHornetTest because of its static
 * nature.  Having two tests use the same sample test class messes up the counters.
 */
public class HornetSampleTimedTest {

  public static int testCount;

  public static void initCounters() {
    testCount = 0;
  }

  @Test
  public void testSomething() throws Exception {
    testCount += 1;
    //System.out.println("TEST SOMETHING");
    assertEquals(1, 1);
  }

}
