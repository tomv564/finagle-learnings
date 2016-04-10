import com.twitter.finagle.Http
import com.twitter.util.Await
import net.lag.kestrel.PersistentQueue
import io.tomv.timing.common.LocalQueue


package io.tomv.timing {

	import com.twitter.finagle.Thrift
	import com.twitter.util.Future
	import io.tomv.timing.registration.thrift.RegistrationService
	import io.tomv.timing.results.thrift.ResultsService

	object Main extends App {

		def resolve(serviceName: String, port: Int) : String = {
			sys.env.get("RESOLVE_CLIENTS") match {
				case Some("HOSTNAME") => serviceName + ":" + port.toString
				case _ => "localhost:" + port
			}
		}

		val publisher = new KestrelQueuePublisher(resolve("kestrel", 22133), "timingevents")
		val registrationClient = Thrift.newIface[RegistrationService[Future]](resolve("registration", 6000))
		val resultsClient = Thrift.newIface[ResultsService[Future]](resolve("results", 7000))

		val gatewayService = new GatewayService(publisher, registrationClient, resultsClient)

		val server = Http.serve(":8080", gatewayService.router)
		Await.ready(server)
	}
}

