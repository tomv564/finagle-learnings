import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.Thrift
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.{Await, Future}
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.http.path.{Root, /}
import com.twitter.finagle.http.Method
import com.fasterxml.jackson.databind.{ObjectMapper, DeserializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import java.io.ByteArrayOutputStream
import com.twitter.util.Duration
import scala.collection.mutable
import scala.util.{Try, Success, Failure}
import io.tomv.timing.registration.thrift.RegistrationService
import io.tomv.timing.registration.Registration
import io.tomv.timing.results.thrift.{Result, TimingEvent}

import com.twitter.finagle.util.HashedWheelTimer
import net.lag.kestrel.{PersistentQueue, LocalDirectory}
import net.lag.kestrel.config.{QueueConfig, QueueBuilder}
import com.twitter.conversions.time._
import com.twitter.concurrent.NamedPoolThreadFactory
import java.util.concurrent._
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.transport.TMemoryBuffer
import java.util.Arrays


// package finagletest {
	object EventType {
		val CHIP_START = 1
		val CHIP_FINISH = 2
		val CHIP_LAP = 3
		val MANUAL_START = 10
		val MANUAL_FINISH = 11
		val MANUAL_LAP = 12
	}

	// // case class Registration(chipNumber: String, name: String, category: String)
	// case class TimingEvent(`type`: Int,
	// 	timeStamp: Long,
	// 	chipNumber: Option[String],
	// 	category: Option[String]
	// )
	// case class Result(category: String, rank: Int, name: String, overallRank: Int, time: String)

	object GatewayService {

		val mapper = new ObjectMapper()
		mapper.registerModule(DefaultScalaModule)
		mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true)
		val registrationClient = Thrift.newIface[RegistrationService[Future]]("localhost:6000")

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
		val timingEventQueue = new PersistentQueue("timingevents", new LocalDirectory("/Users/tomv/.kestrel", journalSyncScheduler), build(), timer)
		timingEventQueue.setup()

		// (val name: String, persistencePath: String,
  //                     @volatile var config: QueueConfig, timer: Timer,
  //                     queueLookup: Option[(String => Option[PersistentQueue])]) {

		val alwaysOK = new Service[Request, Response] {
		  def apply(req: Request): Future[Response] =
		    Future.value(
		      Response(req.version, Status.Ok)
		    )
		}

		// val testPerson = Registration("AB1234", "Test Person", "M3040")
		// val registrations = mutable.MutableList[Registration](testPerson)
		var results = List[Result]()
		val events = mutable.MutableList[TimingEvent]()

		def isValidRegistration(reg: Registration): Boolean =
			(!reg.name.isEmpty() && !reg.category.isEmpty())


		def echoService(message: String) = new Service[Request, Response] {
		    def apply(req: Request): Future[Response] = {
		      val rep = Response(req.version, Status.Ok)
		      rep.setContentString(message)
		      Future(rep)
		    }
		  }

		def listRegistrationsService() = new Service[Request, Response] {
			def apply(req: Request): Future[Response] = {
				registrationClient.getAll() map {
					registrations =>
						val out = new ByteArrayOutputStream
						mapper.writeValue(out, registrations)
					 	val r = Response(req.version, Status.Ok)
					 	r.setContentTypeJson()
					 	r.setContentString(out.toString())
					 	r
				}
			}
		}

		def listResultsService() = new Service[Request, Response] {
			def apply(req: Request): Future[Response] = {
				val out = new ByteArrayOutputStream
				mapper.writeValue(out, results)
			 	val r = Response(req.version, Status.Ok)
			 	r.setContentTypeJson()
			 	r.setContentString(out.toString())
			 	Future(r)
			}
		}

		def invalidRequest(req: Request) : Future[Response] = {
			Future(Response(req.version, Status.BadRequest))
		}

		def createRegistrationService() = new Service[Request, Response] {
			def apply(req: Request): Future[Response] = {

				Try(req withReader { r => mapper.readValue(r, classOf[Registration]) }) match {
					case Success(reg) => {
						if (isValidRegistration(reg)) {



							registrationClient.create(reg.name, reg.category) map {
								created => Response(req.version, Status.Created)
							}

						} else {
							Future(Response(req.version, Status.NotImplemented))//invalidRequest(req)
						}
					}
					case Failure(ex) => {
						print(ex)
						invalidRequest(req)
					}
				}
			}
		}

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

		def createTimingEventService() = new Service[Request, Response] {
			def apply(req: Request): Future[Response] = {
				Try(req withReader { r => mapper.readValue(r, classOf[TimingEvent]) }) match {
					case Success(event) => {

						val buffer = new TMemoryBuffer(512)
						val protocol = new TBinaryProtocol(buffer)
						event.write(protocol)

						val bytes = Arrays.copyOfRange(buffer.getArray(), 0, buffer.length())
						timingEventQueue.add(bytes, None)

							// reg.write(protocol)

						// Registration.write(new Registration(), protocol)


						// events += event
						// updateResults(events) map {
						// 	updatedResults => results = updatedResults.toList
						// }
						val r = Response(req.version, Status.Created)
						Future(r)
					}
					case Failure(ex) => invalidRequest(req)
				}

			}
		}

		val router = RoutingService.byMethodAndPathObject[Request] {
		    case (Method.Post, Root / "echo" / message) => echoService(message)
		    case (Method.Get, Root / "registrations") => listRegistrationsService
		    case (Method.Post, Root / "registrations") => createRegistrationService
		    case (Method.Get, Root / "results") => listResultsService
		    case (Method.Post, Root / "events") => createTimingEventService
		    // case _ => alwaysOK
		}

	}


// }