import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.Thrift
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.{Await, Future}
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.http.path.{Root, /}
import com.twitter.finagle.http.Method
import com.twitter.logging.Logger
import com.fasterxml.jackson.databind.{ObjectMapper, DeserializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import java.io.ByteArrayOutputStream
import scala.collection.mutable
import scala.util.{Try, Success, Failure}

import io.tomv.timing.registration.thrift.RegistrationService
import io.tomv.timing.registration.Registration

import io.tomv.timing.results.thrift.ResultsService
import io.tomv.timing.results.{TimingEvent, Result}

package io.tomv.timing {

	import com.twitter.finagle.kestrel.protocol.Stored

	class GatewayService(eventPublisher: QueuePublisher, registrationClient: RegistrationService[Future], resultsClient: ResultsService[Future]) {

		val mapper = new ObjectMapper()
		mapper.registerModule(DefaultScalaModule)
//		mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true)
	    private val log = Logger.get(getClass)


		val alwaysOK = new Service[Request, Response] {
		  def apply(req: Request): Future[Response] =
		    Future.value(
		      Response(req.version, Status.Ok)
		    )
		}

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
						log.info("Returning registrations")
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
				resultsClient.getAll() map {
					results =>
						log.info("Returning results")
						val out = new ByteArrayOutputStream
						mapper.writeValue(out, results)
					 	val r = Response(req.version, Status.Ok)
					 	r.setContentTypeJson()
					 	r.setContentString(out.toString())
					 	r
				}

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
								registration =>
									log.info("Created %s for %s", registration.chipNumber, registration.name)
									val r = Response(req.version, Status.Created)
									val out = new ByteArrayOutputStream
									mapper.writeValue(out, registration)
									r.setContentTypeJson()
									r.setContentString(out.toString())
									r
							}
						} else {
							log.info("Invalid Registration")
							Future(Response(req.version, Status.NotImplemented))//invalidRequest(req)
						}
					}
					case Failure(ex) => {
						log.error(ex, "Could not parse Registration", ex.getMessage)
						invalidRequest(req)
					}
				}
			}
		}


		def createTimingEventService() = new Service[Request, Response] {
			def apply(req: Request): Future[Response] = {
				Try(req withReader { r => mapper.readValue(r, classOf[TimingEvent]) }) match {
					case Success(event) => {
						eventPublisher.publish(event) map {
							r => r match {
								case Stored() => {
									log.info("%s %s STORED", event.chipNumber, event.`type`)
									Response(req.version, Status.Created)
								}
								case other => {
									log.info("%s %s %s", event.chipNumber, event.`type`, other)
									Response(req.version, Status.InternalServerError)
								}
							}
						}
				  	}
					case Failure(ex) => {
						log.error(ex, "Could not parse TimingEvent", ex.getMessage)
						invalidRequest(req)
					}
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


}