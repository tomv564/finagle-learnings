import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.{Await, Future}
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.http.path.{Root, /}
import com.twitter.finagle.http.Method
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import java.io.ByteArrayOutputStream

case class Registration (
	chipNumber: String,
	name: String,
	category: String
)

object Main extends App {

	val mapper = new ObjectMapper()
	mapper.registerModule(DefaultScalaModule)


	val alwaysOK = new Service[Request, Response] {
	  def apply(req: Request): Future[Response] =
	    Future.value(
	      Response(req.version, Status.Ok)
	    )
	}

	val testPerson = Registration("AB1234", "Test Person", "M3040")
	val registrations = List[Registration](testPerson)

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

	val router = RoutingService.byMethodAndPathObject[Request] {
	    case (Method.Post, Root / "echo" / message) => echoService(message)
	    case (Method.Get, Root / "registrations") => listRegistrationsService
	    case _ => alwaysOK
	}

	val server = Http.serve(":8080", router)
	Await.ready(server)
}

