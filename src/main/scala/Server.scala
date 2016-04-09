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

		val publisher = new KestrelQueuePublisher("localhost:22133", "timingevents")
		val registrationClient = Thrift.newIface[RegistrationService[Future]]("localhost:6000")
		val resultsClient = Thrift.newIface[ResultsService[Future]]("localhost:7000")

		val gatewayService = new GatewayService(publisher, registrationClient, resultsClient)

		val server = Http.serve(":8080", gatewayService.router)
		Await.ready(server)
	}
}

