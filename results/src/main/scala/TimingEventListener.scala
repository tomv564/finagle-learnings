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

	class TimingEventListener(queue: PersistentQueue, results: mutable.ArrayBuffer[Result]) extends Runnable {

		val events = mutable.MutableList[thrift.TimingEvent]()

		val registrationClient = Thrift.newIface[RegistrationService[Future]]("localhost:6000")

		val timeout = 250.milliseconds.fromNow

		@volatile
		var stopped : Boolean = false

		def stop() : Unit = {
			stopped = true
		}

//		listen(queue)

		def createProtocol(bytes: Array[Byte]) : TProtocol = {
			val buffer = new TMemoryBuffer(512)
			buffer.write(bytes, 0, bytes.length)
			new TBinaryProtocol(buffer)
		}

			def run = {
				stopped = false
				listen()
			}

	    def listen() : Unit = {
				while (!stopped) {
					queue.waitRemove(Some(timeout), false).onSuccess { item =>
						item.foreach { e =>
							val prot = createProtocol(e.data)
							val event = thrift.TimingEvent.decode(prot)
							events += event
							updateResults(events) foreach {
								updatedResults =>
									results.clear()
									results ++= updatedResults
							}
						}
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