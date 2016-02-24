import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.{Await, Future}
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.http.path.{Root, /}

object Main extends App {

	val alwaysOK = new Service[Request, Response] {
	  def apply(req: Request): Future[Response] =
	    Future.value(
	      Response(req.version, Status.Ok)
	    )
	}

	def echoService(message: String) = new Service[Request, Response] {
	    def apply(req: Request): Future[Response] = {
	      val rep = Response(req.version, Status.Ok)
	      rep.setContentString(message)
	      Future(rep)
	    }
	  }

	val router = RoutingService.byPathObject[Request] {
	    // case Root / "user" / Integer(id) => userService(id)
	    case Root / "echo"/ message => echoService(message)
	    case _ => alwaysOK
	}

	val server = Http.serve(":8080", router)
	Await.ready(server)
}

