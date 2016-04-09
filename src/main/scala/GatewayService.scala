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
import scala.collection.mutable
import scala.util.{Try, Success, Failure}

import io.tomv.timing.registration.thrift.RegistrationService
import io.tomv.timing.registration.Registration

import io.tomv.timing.results.thrift.ResultsService
import io.tomv.timing.results.{TimingEvent, Result}

import net.lag.kestrel.PersistentQueue
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.transport.TMemoryBuffer
import java.util.Arrays

package io.tomv.timing {

	class GatewayService(eventPublisher: QueuePublisher, registrationClient: RegistrationService[Future], resultsClient: ResultsService[Future]) {

		val mapper = new ObjectMapper()
		mapper.registerModule(DefaultScalaModule)
		mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true)

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


		def createTimingEventService() = new Service[Request, Response] {
			def apply(req: Request): Future[Response] = {
				Try(req withReader { r => mapper.readValue(r, classOf[TimingEvent]) }) match {
					case Success(event) => {
						eventPublisher.publish(event)

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


}