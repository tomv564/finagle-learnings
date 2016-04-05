import java.net.InetSocketAddress
import com.twitter.finagle.Thrift
import com.twitter.util.Await


import com.twitter.finagle.util.HashedWheelTimer
import net.lag.kestrel.{PersistentQueue, LocalDirectory}
import net.lag.kestrel.config.{QueueConfig, QueueBuilder}
import com.twitter.conversions.time._
import com.twitter.concurrent.NamedPoolThreadFactory
import java.util.concurrent._
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.transport.TMemoryBuffer
import java.util.Arrays
import com.twitter.scrooge.ThriftStruct
import org.apache.thrift.protocol._
import scala.collection.mutable
// import scala.collection.List
import com.twitter.util.Future
import com.twitter.conversions.time._

import io.tomv.timing.registration.thrift.RegistrationService


package io.tomv.timing.results {

	object Main extends App {

		def createQueue(name: String) : PersistentQueue = {
		    val queueConfig = new QueueBuilder()
		    val journalSyncScheduler =
		      new ScheduledThreadPoolExecutor(
		        Runtime.getRuntime.availableProcessors,
		        new NamedPoolThreadFactory("journal-sync", true),
		        new RejectedExecutionHandler {
		          override def rejectedExecution(r: Runnable, executor: ThreadPoolExecutor) {
		            // log.warning("Rejected journal fsync")
		          }
		        })
		    val timer = HashedWheelTimer(100.milliseconds)
		    val build = new QueueBuilder()
		    val queue = new PersistentQueue(name, new LocalDirectory("/Users/tomv/.kestrel", journalSyncScheduler), build(), timer)
		    queue.setup()
		    queue
		}

		var eventQueue : PersistentQueue = createQueue("timingevents")
		val results = mutable.ArrayBuffer[Result]()

		val listener = new TimingEventListener(eventQueue, results)

		val service = new ResultsServiceImpl(results)
		val server = Thrift.serveIface(new InetSocketAddress(6000), service)
		Await.ready(server)
	}

}
