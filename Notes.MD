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

## Un-monolith


* Micro services, all in one project, multiple sbt projects. 
* How to share references?

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
* Rewrite everything to map Futures and or collect them.

### Test fixes

Registration was unknown.
What about `cannot deserialize out of START_OBJECT token` (empty response - http 500 status is seen as succcess by finagle - but mostly, cannot serialize traits from generated thrift)
Exception propagated to the root monitor (use whenReady to handle Futures in tests.)

### Evictions

Somehow scrooge-core 4.5 was installed.
Running 'evictions' revealed that jackson and scala base libraries were mixed up. How to fix?

### Events service

Event stream, high requests per second needed. Post events to a queue.

Kestrel is a simple thrift-compatible queue written in Scala from Twitter
Can be used as a library, see 
https://twitter.github.io/kestrel/docs/guide.html 


### Results service

Consumes from a queue.
* define the external service handlers needed and mock data on them
    * Registrations (Participant(name, category, chipNumber), add, list)
    * Recording (chipStart, chipFinish, gunStart ())
    * Results (participants by category, ordered by (chipFinish-gunStart))


### Next up: clean up

* Extract in-process queue creation to a single utility - DONE
* Clean up old dependencies (eg jackson) NOT DONE.
    - install sbt-dependency-graph plugin (globally in ~/.sbt)
    - run sbt dependencyBrowseGraph or something
    - Put exclude("com.fasterxml.jackson.module", "jackson-module-scala") on the offending outdated dependency. https://www.packtpub.com/books/content/dependency-management-sbt
* Switch to finagle-kestrel
    - Reading - DONE
    - Writing (Gateway) - DONE


### Then: run a single instance of the whole stack 

Some nice-to-haves:
* Logging
* Smarter reading/writing thrift to/from Buf (bijections??)
* TwitterServer all the things


* Standalone kestrel

twitter's sbt-package-dist plugin is old, do it manually
run `sbt package`
create /dist/{package name}/
copy src/scripts to above dir
copy config/*.scala to above dir and adjust config.
edit base.sh:
`SBT_JAR=kestrel-assembly-2.4.8-SNAPSHOT.jar`


ostrich is missing, what to do? 
There was a 2.11 version, use that.

use sbt-assembly to make a fat jar
conflicts when same class is redefined:
org.slf4j/slf4j-jdk14/jars/slf4j-jdk14-1.7.7.jar
org.slf4j/slf4j-log4j12/jars/slf4j-log4j12-1.7.5
StaticMDCBinder
StaticLoggerBinder
finagle-memcached was the culprit. exclude("org.slf4j", "slf4j-jdk14")


* Everything listens to a specified url

Kestrel:
  memcacheListenPort = 22133
  textListenPort = 2222
  thriftListenPort = 2229

Gateway: 8080
Registration: 6000
Results: 7000

#### Integration tests

* Curl test or reuse unit test?

Integration tests (under src/it, enable sbt configuration) 
Run with `it:test`
By default not ran on normal builds.

Things Learned:
* Check output values from any client call as errors are rarely exceptions.
* Use Array[MyType] to deserialize with jackson or you will get Map[Map[String, String]]

### Then: run a single instance on the single mesos slave

Planning: Local docker is nice but not needed for deployment.
Dlite while waiting for docker's own beta, easy quick install on brew.
https://github.com/nlf/dlite/tree/master

Put images on the mesos master? How will mesosphere pull the images?

* package into docker containers

update build.sbt: enablePlugins(DockerPlugin)
then: `sbt docker:publishLocal`
uses java:latest from docker hub by default.

* run packaged containers on local docker

1.x version of dlite doesn't properly return stdout, so use `run -i` to test

docker run -d -p 8080:8080 root:1.0
docker run -p 8080 --rm

TODO: label latest??
Yes, add `dockerUpdateLatest := true`

* define apps in mesosphere 
* fix any service location issues.

### Then: database

* local mysql instance (docker?)
* finagle-mysql for stateful services

### Then: cluster

* Zookeeper serversets for everything
* Multiple queue hosts?

### Next up:

* research what finagle properties apply to these services (http://alexmartins.me/2015/12/30/building-fault-tolerant-clients-with-finagle-part-1.html)
    - throttling?
    - live results over websocket
    - running in a twitterserver
* write some tests