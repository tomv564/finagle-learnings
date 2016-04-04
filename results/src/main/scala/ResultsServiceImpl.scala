import io.tomv.timing.results.thrift
import io.tomv.timing.registration.thrift.RegistrationService

import com.twitter.finagle.Thrift

import com.twitter.util.Future
import scala.collection.mutable

package io.tomv.timing.results {

  case class TimingEvent(`type`: Int, timeStamp: Long, chipNumber: Option[String], category: Option[String]) extends thrift.TimingEvent {
  }

  case class Result(
  rank: Int,
  overallRank: Int,
  name: String,
  category: String,
  time: String) extends thrift.Result


  object EventType {
    val CHIP_START = 1
    val CHIP_FINISH = 2
    val CHIP_LAP = 3
    val MANUAL_START = 10
    val MANUAL_FINISH = 11
    val MANUAL_LAP = 12
  }

  class ResultsServiceImpl extends thrift.ResultsService[Future] {

    val results = mutable.MutableList[thrift.Result]()
    val registrationClient = Thrift.newIface[RegistrationService[Future]]("localhost:6000")


      // events += event
            // updateResults(events) map {
            //  updatedResults => results = updatedResults.toList
            // }

    def createResult(chipNumber: String, startEvent: TimingEvent, finishEvent: TimingEvent) : Future[Result] = {
      registrationClient.get(chipNumber) map {
        reg => Result(1, 1, reg.name, reg.category, (finishEvent.timeStamp - startEvent.timeStamp).toString)
      }
    }

    def parseChipEvents(chipEvents: Seq[TimingEvent]) : Option[Future[Result]] = {
      for {
        startEvent <- chipEvents.find(e => e.`type` == EventType.CHIP_START)
        finishEvent <- chipEvents.find(e => e.`type` == EventType.CHIP_FINISH)
        chipNumber <- startEvent.chipNumber
      } yield createResult(chipNumber, startEvent, finishEvent)
    }

    def updateResults(events: Seq[TimingEvent]) : Future[Seq[Result]] = {
      val grouped = events.groupBy(_.chipNumber)
      val updatedResults = grouped.flatMap { pair => parseChipEvents(pair._2)}.toSeq
      Future.collect(updatedResults)
    }


    override def getAll(): Future[Seq[thrift.Result]] = Future.value(results)
//    implement:
//      https://www.codatlas.com/github.com/twitter/finagle/HEAD/finagle-example/src/main/scala/com/twitter/finagle/example/kestrel/KestrelClient.scala

  }

}