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

organization in ThisBuild := "com.spotify"

name := "hornet"

scalaVersion in ThisBuild := "2.11.8"

libraryDependencies ++= Seq(
  "junit" % "junit" % "4.12",
  "com.typesafe.akka" % "akka-actor_2.11" % "2.4.0",
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
  "org.mockito" % "mockito-core" % "1.10.19" % "test"
)

lazy val release = taskKey[Unit]("Zips artifacts for release")

release := {
  val jar: File = assembly.value
  val bin: File = baseDirectory.value / "bin" / "hornet"
  val ver: String = (version in ThisBuild).value

  val out: File = target.value / ("hornet-" + ver + ".zip")
  val inputs: Seq[(File,String)] = Seq(jar, bin) x Path.flat
  IO.zip(inputs, out)
}
