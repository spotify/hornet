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

import com.spotify.hornet.log.Logger
import org.scalatest.FunSuite
import org.scalatest.Assertions.assert

import java.io.StringReader
import java.io.StringWriter
import scala.collection.mutable.ArrayBuffer

class RecordTest extends FunSuite {

  test("test duration") {
    val r1 = Record("r1", 1, true, 10, 30)
    assert(r1.duration == 20)
    assert("Record(r1,1,true,10,30)" == r1.toString)
  }
}


class CsvRecordReaderTest extends FunSuite {

  test("empty") {
    val reader = new CsvRecordReader(new StringReader(""))
    assert(!reader.hasNext)
  }

  test("iteration") {
    val data = """name,thread_count,successful,start,end,duration
test,5,true,1373334374861,1373334375424,563
test,5,true,1373334374859,1373334375424,565
test,5,true,1373334374859,1373334375424,565"""
    val reader = new CsvRecordReader(new StringReader(data))
    val actual = new ArrayBuffer[Record]()
    for (r <- reader) {
      actual += r
    }
    assert(3 == actual.size)
    assert(Record("test", 5, true, 1373334374861L, 1373334375424L) == actual(0))
    assert(Record("test", 5, true, 1373334374859L, 1373334375424L) == actual(1))
    assert(Record("test", 5, true, 1373334374859L, 1373334375424L) == actual(2))
  }
}


class CsvRecordWriterTest extends FunSuite {

  // test("empty") {
  //   Logger.verbose = true
  //   val out = new StringWriter()
  //   val writer = new CsvRecordWriter(out)
  //   writer.close
  //   assert("name,thread_count,successful,start,end,duration\n" == out.toString)
  // }

  test("one thread count") {
    val out = new StringWriter();
    val writer = new CsvRecordWriter(out);
    writer.write(Record("test", 5, true, 1373334374861L, 1373334375424L))
    writer.write(Record("test", 5, true, 1373334374859L, 1373334375424L))
    writer.write(Record("test", 5, true, 1373334374859L, 1373334375424L))
    writer.close
    assert("""name,thread_count,successful,start,end,duration
test,5,true,1373334374861,1373334375424,563
test,5,true,1373334374859,1373334375424,565
test,5,true,1373334374859,1373334375424,565
""" == out.toString())
  }

}
