import com.twitter.finagle.Http
import com.twitter.util.Await

// package finagletest {

	object Main extends App {
		val server = Http.serve(":8080", RegistrationService.router)
		Await.ready(server)
	}
// }

