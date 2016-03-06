import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.{Await, Future}
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.http.path.{Root, /}
import com.twitter.finagle.http.Method
import com.fasterxml.jackson.databind.{ObjectMapper, DeserializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import java.io.ByteArrayOutputStream
import scala.collection.mutable
import scala.util.{Try, Success, Failure}

// package finagletest {
	object EventType {
		val CHIP_START = 1
		val CHIP_FINISH = 2
		val CHIP_LAP = 3
		val MANUAL_START = 10
		val MANUAL_FINISH = 11
		val MANUAL_LAP = 12
	}

	case class Registration(chipNumber: String, name: String, category: String)
	case class TimingEvent(`type`: Int,
		timeStamp: Long,
		chipNumber: Option[String],
		category: Option[String]
	)
	case class Result(category: String, rank: Int, name: String, overallRank: Int, time: String)

	object GatewayService {

		val mapper = new ObjectMapper()
		mapper.registerModule(DefaultScalaModule)
		mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true)

		val alwaysOK = new Service[Request, Response] {
		  def apply(req: Request): Future[Response] =
		    Future.value(
		      Response(req.version, Status.Ok)
		    )
		}

		val testPerson = Registration("AB1234", "Test Person", "M3040")
		val registrations = mutable.MutableList[Registration](testPerson)
		var results = List[Result]()
		val events = mutable.MutableList[TimingEvent]()

		def isValidRegistration(reg: Registration): Boolean =
			(!reg.name.isEmpty() && !reg.chipNumber.isEmpty() && !reg.category.isEmpty())


		def echoService(message: String) = new Service[Request, Response] {
		    def apply(req: Request): Future[Response] = {
		      val rep = Response(req.version, Status.Ok)
		      rep.setContentString(message)
		      Future(rep)
		    }
		  }

		def listRegistrationsService() = new Service[Request, Response] {
			def apply(req: Request): Future[Response] = {

				val out = new ByteArrayOutputStream
				mapper.writeValue(out, registrations)
			 	val r = Response(req.version, Status.Ok)
			 	r.setContentTypeJson()
			 	r.setContentString(out.toString())
			 	Future(r)
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
							registrations += reg
							val r = Response(req.version, Status.Created)
							Future(r)
						} else {
							invalidRequest(req)
						}
					}
					case Failure(ex) => invalidRequest(req)
				}
			}
		}

		def parseChipEvents(chipEvents: Seq[TimingEvent]) : Option[Result] = {
			for {
				startEvent <- chipEvents.find(e => e.`type` == EventType.CHIP_START)
				finishEvent <- chipEvents.find(e => e.`type` == EventType.CHIP_FINISH)
				chipNumber <- startEvent.chipNumber
				reg <- registrations.find(r => r.chipNumber == chipNumber)
			} yield Result(reg.category, 1, reg.name, 1, (finishEvent.timeStamp - startEvent.timeStamp).toString)
		}

		def updateResults(events: Seq[TimingEvent]) : List[Result] = {
			val grouped = events.groupBy(_.chipNumber)
			grouped.flatMap { pair => parseChipEvents(pair._2)}.toList
		}

		def createTimingEventService() = new Service[Request, Response] {
			def apply(req: Request): Future[Response] = {
				Try(req withReader { r => mapper.readValue(r, classOf[TimingEvent]) }) match {
					case Success(event) => {
						events += event
						results = updateResults(events)
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