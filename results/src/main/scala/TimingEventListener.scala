import java.net.InetSocketAddress
import com.twitter.finagle.Thrift
import com.twitter.util.Await

import io.tomv.timing.results.thrift


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

	import java.util.concurrent.atomic.AtomicBoolean

	import com.twitter.finagle.builder.ClientBuilder
	import com.twitter.finagle.kestrel.protocol.Kestrel
	import com.twitter.finagle.kestrel.{ReadMessage, ReadHandle, Client}
	import com.twitter.finagle.service.Backoff
	import com.twitter.io.Buf
	import com.twitter.util.JavaTimer
	import net.lag.kestrel.QItem

	class TimingEventListener(queue: PersistentQueue, results: mutable.ArrayBuffer[Result]) extends Runnable {

		val events = mutable.MutableList[thrift.TimingEvent]()

		val registrationClient = Thrift.newIface[RegistrationService[Future]]("localhost:6000")

		val timeout = 250.milliseconds.fromNow

		//val stopped = new AtomicBoolean(false)

		@volatile
		var stopped : Boolean = false

		def stop() : Unit = {
			stopped = true
		}

		def createProtocol(bytes: Array[Byte]) : TProtocol = {
			val buffer = new TMemoryBuffer(512)
			buffer.write(bytes, 0, bytes.length)
			new TBinaryProtocol(buffer)
		}

			def run = {
				stopped = false
				listenLocal()
			}

	    def listenLocal() : Unit = {
				while (!stopped) {
					queue.waitRemove(Some(timeout), false).onSuccess { item =>
						item.foreach { e =>
							handleQItem(e)
						}
					}
				}
	    }

			def handleMessage(msg: ReadMessage) : Unit = {
				val bytes = new Array[Byte](msg.bytes.length)
				msg.bytes.write(bytes, 0)
				val buffer = new TMemoryBuffer(512)
				buffer.write(bytes, 0, bytes.length)
				val prot = new TBinaryProtocol(buffer)
				val event = thrift.TimingEvent.decode(prot)
				handleEvent(event)
			}


			def handleQItem(item: QItem) : Unit = {
				val prot = createProtocol(item.data)
				val event = thrift.TimingEvent.decode(prot)
				handleEvent(event)
			}

		def handleEvent(event: thrift.TimingEvent): Unit = {
			events += event
			updateResults(events) foreach {
				updatedResults =>
					results.clear()
					results ++= updatedResults
			}

		}



			def listen() : Unit = {

				// Add "host:port" pairs as needed
				val host = "localhost:22133"

				val client = Client(ClientBuilder()
					.codec(Kestrel())
					.hosts(host)
					.hostConnectionLimit(1) // process at most 1 item per connection concurrently
					.buildFactory())

				val queueName = "queue"
				val timer = new JavaTimer(isDaemon = true)
				val retryBackoffs = Backoff.const(10.milliseconds)
				val readHandle: ReadHandle = client.readReliably(queueName, timer, retryBackoffs)

				// Attach an async error handler that prints to stderr
				readHandle.error foreach { e =>
					 System.err.println("zomg! got an error " + e)
				}

				// Attach an async message handler that prints the messages to stdout
				readHandle.messages foreach { msg =>
					try {
						val Buf.Utf8(str) = msg.bytes
						//handleItem(msg.bytes)
					} finally {
						msg.ack.sync() // if we don't do this, no more msgs will come to us
					}
				}

			}

	    def createResult(chipNumber: String, startEvent: thrift.TimingEvent, finishEvent: thrift.TimingEvent) : Future[Result] = {
	      registrationClient.get(chipNumber) map {
	        reg => Result(1, 1, reg.name, reg.category, (finishEvent.timeStamp - startEvent.timeStamp).toString)
	      }
	    }

	    def parseChipEvents(chipEvents: Seq[thrift.TimingEvent]) : Option[Future[Result]] = {
	      for {
	        startEvent <- chipEvents.find(e => e.`type` == EventType.CHIP_START)
	        finishEvent <- chipEvents.find(e => e.`type` == EventType.CHIP_FINISH)
	        chipNumber <- startEvent.chipNumber
	      } yield createResult(chipNumber, startEvent, finishEvent)
	    }

	    def updateResults(events: Seq[thrift.TimingEvent]) : Future[Seq[Result]] = {
	      val grouped = events.groupBy(_.chipNumber)
	      val updatedResults = grouped.flatMap { pair => parseChipEvents(pair._2)}.toSeq
	      Future.collect(updatedResults)
	    }

	}
}