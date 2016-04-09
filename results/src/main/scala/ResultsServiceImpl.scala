import io.tomv.timing.results.thrift

import com.twitter.finagle.Thrift

import net.lag.kestrel._

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

  class ResultsServiceImpl(results: Seq[thrift.Result]) extends thrift.ResultsService[Future] {
    override def getAll(): Future[Seq[thrift.Result]] = Future.value(results)
  }

}