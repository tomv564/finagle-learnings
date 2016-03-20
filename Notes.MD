### First simple finagle app

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


### Registration service

Use RoutingService to split route to GET/POST
curl -D - localhost:8080/registrations
Create a case class and a list
Find a JSON library (use jackson's scala module, look on maven, check %%!)
sbt reload, sbt update.

Jackon on Scala case classes: use ctor methods and configure FAIL_ON_MISSING_CREATOR_PROPERTIES

Now try
curl -i -H "Content-Type: application/json" -X POST -d '{}' localhost:8080/registrations
url -i -H "Content-Type: application/json" -X POST -d '{"chipNumber": "AB1111", "name": "Tom van Ommeren", "category": "M4050"}' localhost:8080/registrations
curl -i localhost:8080/registrations


### Convert to a gateway, add some services

Why gateways? API for web clients, coarse grained calls, encapsulate auth etc + marshalling to JSON, etc.
http://techblog.netflix.com/2013/01/optimizing-netflix-api.html

Start extracting client logic.
Generics in scala (for json parsing) are tricky.


### Add processing

Events are essentially a stream. They need to be processed and stored.


### The database story

HBase is meant for high write volume.
Let's start with in-memory, but finagle-mysql woudl be nice to add to test scaling realistically.
The problem (see: ) is sharding myself for time-series: One shard is always hot.

### Un-monolith

Translate 3 services to thrift
Create 3 projects in the build.sbt
Make sure each has its own launcher/entrypoint.
TODO: So what is src/main??
No main class detected. Hardcoding mainClass in settings also does not work. WTF?

Path layout not documented by the sbt documentation
https://github.com/jbruggem/sbt-multiproject-example


### Fix namespaces

io.tomv.timing

### Thriftify

Installed finagle-thrift, scrooge-core and scrooge-plugin in plugins.sbt
Created registrations.thrift in src/thrift. Ran scroogeGen.
Files ended up in `target/scala-2.11/src_managed/` + a long namespace.
After removing namespace defs, files ended up in package 'thrift'.

Need to create an Impl class for the generated thrift interface
Then got stuck with missing implementations for serviceToFunction and functionToService. Updated scrooge-core and scrooge-sbt-plugin?, fixed.

Thrift client:
* Import to registrations generated code
* add finagle-thrift

### Evictions

Somehow scrooge-core 4.5 was installed.
Running 'evictions' revealed that jackson and scala base libraries were mixed up. How to fix?

### Next up:


* Micro services, all in one project, multiple sbt projects. 
* How to share references?

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