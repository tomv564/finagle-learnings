import io.tomv.timing.results.thrift

package io.tomv.timing.results {

  case class TimingEvent(`type`: Int, timeStamp: Long, chipNumber: Option[String], category: Option[String]) extends thrift.TimingEvent {
  }

  case class Result(
  rank: Int,
  overallRank: Int,
  name: String,
  category: String,
  time: String) extends thrift.Result

  class ResultsServiceImpl {

  }

}