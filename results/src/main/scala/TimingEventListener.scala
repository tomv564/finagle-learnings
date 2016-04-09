import com.twitter.finagle.Thrift
import scala.collection.mutable
import com.twitter.util.Future

import io.tomv.timing.registration.thrift.RegistrationService

package io.tomv.timing.results {

	trait TimingEventHandler {
		def handleEvent(event: thrift.TimingEvent): Unit
	}

	class ResultTimingEventHandler(results: mutable.ArrayBuffer[Result], registrationClient: RegistrationService[Future]) extends TimingEventHandler {

		val events = mutable.MutableList[thrift.TimingEvent]()

		def handleEvent(event: thrift.TimingEvent): Unit = {
			events += event
			updateResults(events) foreach {
				updatedResults =>
					results.clear()
					results ++= updatedResults
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