Install SBT

Follow twitter's quickstart
Set scalaVersion to something recent in build.sbt

http://twitter.github.io/finagle/guide/Quickstart.html

Add routing:

https://gist.github.com/vkostyukov/10147216

Added: echoService and RoutingService

Now can try:

curl -D - localhost:8080
curl -D - localhost:8080/echo/lol

Finch's benchmarks provide a simple configuration of finagle REST
https://github.com/finagle/finch/blob/master/benchmarks/src/main/scala/io/finch/benchmarks/service/finagle/benchmark.scala

Next up:

* define the external service handlers needed and mock data on them
    * Registrations (Participant(name, category, chipNumber), add, list)
    * Recording (chipStart, chipFinish, gunStart ())
    * Results (participants by category, ordered by (chipFinish-gunStart))
* research what finagle properties apply to these services (http://alexmartins.me/2015/12/30/building-fault-tolerant-clients-with-finagle-part-1.html)
    - throttling?
    - live results over websocket
    - running in a twitterserver
* write some tests
* set up the thrift-based services