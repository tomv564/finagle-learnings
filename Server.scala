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

case class Registration(chipNumber: String, name: String, category: String) (
)

object Main extends App {

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

	val router = RoutingService.byMethodAndPathObject[Request] {
	    case (Method.Post, Root / "echo" / message) => echoService(message)
	    case (Method.Get, Root / "registrations") => listRegistrationsService
	    case (Method.Post, Root / "registrations") => createRegistrationService
	    case _ => alwaysOK
	}

	val server = Http.serve(":8080", router)
	Await.ready(server)
}

