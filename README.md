<img align=right src="https://ghe.spotify.net/eyal/hornet/raw/master/docs/hornet_small.png"></img>

HORNET
=======

Hornet is a Java load test framework written in Scala. It wraps a **[JUnit][3]** test and runs it as
a load test.  This means that you can write load tests in using a familiar framework, i.e. JUnit.
It also means that you can run load tests as part of CI to ensure that they do not become stale.

**Hornet is [Coordinated Omission safe][1]**. The coordinated omission problem is one which many
load test and benchmark tools suffer from.  This is when a pause or slowdown in service response
causes the test tool to back off, effectively sending fewer request and making the average response
time seem higher then it would normally be in production.  Hornet avoids this in two ways: First, it
produces a response time percentile graph which is useful to detect when coordinated omission
happens.  Second, it produces a fixed request per second rate regardless of whether the service
responds in time or not.

Hornet currently produces three types of reports: text, HTML, and CSV.  The reports contain the following
sections:
* Request response time.
* Throughput (request per second).
* Failure rate.
* Request response percentile.

HOW TO USE IN MY PROJECT
---------------------------

See our wiki page on the subject: [How to Use in My Project][2].

EXAMPLES
---------

### Text Report

    HttpLoadTest#testUsername:
                     ▂   ▅   ▆   ▇   █   ▇
       Throughput:  15  65  74  96 101  96
                     ▁   ▂   ▄   ▄   ▆   █
    Response Time:  14  99 213 258 343 464
                     ▁   ▁   ▁   ▁   ▁   ▁
     Failure Rate:   0   0   0   0   0   0
     Thread Count:   1  10  20  30  40  50


### HTML Report
<img src="https://ghe.spotify.net/eyal/hornet/raw/master/docs/hornet_report.png"></img>


[1]: https://www.infoq.com/presentations/latency-pitfalls
[2]: https://ghe.spotify.net/eyal/hornet/wiki/How-to-Use-in-My-Project
[3]: http://junit.org/junit4/

HOW TO BUILD
===============

Check out the code from github.

### Compile

    sbt compile

### Test

    sbt test

### Create Fat Jar

    sbt assembly

### Create Zip

    sbt release


License
=========

Copyright 2016 Spotify AB.

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
