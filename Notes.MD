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


### Deployment

Cluster management. 
Aurora (one big machine model).
Kubernetes (more container-style from Google)
Marathon (container-style for Mesos. Ran at AirBnB, no batch jobs)
Mesosphere (owns Marathon and sells another administrative layer over top?)

Marathon set up:
https://www.digitalocean.com/community/tutorials/how-to-configure-a-production-ready-mesosphere-cluster-on-ubuntu-14-04

Service discovery:

Zookeeper is CP, not necessarily Available (airbnb has added caching to fix this?) https://tech.knewton.com/blog/2014/12/eureka-shouldnt-use-zookeeper-service-discovery/
Etcd (Raft based) or Eureka (Netflix)
http://www.simplicityitself.io/getting/started/with/microservices/2015/06/10/service-discovery-overview.html

### Aurora

 The operational hierarchy is:

* Aurora manages and schedules jobs for Mesos to run.
* Mesos manages the individual tasks that make up a job.
* Thermos manages the individual processes that make up a task.

http://tepid.org/tech/01-aurora-mesos-cluster/

#### What does it do? 

Manages jobs (workers or services?)
Publishes them to Zookeeper
Uses Mesos

To run something, run aurora update start $jobkey $jobfile
Job keys: cluster/role/environment/jobname
Job file: python!
Jobs is an array containing: 

* Service tying together (cluster env role name) to task
* Task (SequentialTask) tying processes[] to Resources(cpu =1, ram = 1*MB, disk=8*mb)
* An install Process (name, cmdline) to copy/download/install the app
* A run Process to start it up. 

### Mesos

Abstracts physical hardware into a virtual pool of resources
Also uses Zookeeper
Containers: Supports Docker, what about rkt?

Shutdown:

* if health port exists it should respond to POST /quitquitquit
* POST /abortabortabort
* SIGTERM
* SIGKILL

### Thermos

Manages tasks?


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