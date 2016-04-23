import com.twitter.finagle.Http
import com.twitter.util.Await
import net.lag.kestrel.PersistentQueue
import io.tomv.timing.common.LocalQueue
import com.twitter.logging.Logger
import com.twitter.finagle.Thrift
import com.twitter.util.Future
import io.tomv.timing.registration.thrift.RegistrationService
import io.tomv.timing.results.thrift.ResultsService

package io.tomv.timing {

	import com.twitter.logging.{ConsoleHandler, Level, LoggerFactory}

	object Main extends App {

		val factory = LoggerFactory(
			node = "",
			level = Some(Level.INFO),
			handlers = List(
				ConsoleHandler()
			)
		)

		val logger = factory()

		private val log = Logger.get(getClass)

		def resolve(serviceName: String, port: Int) : String = {
			val hostPort = sys.env.get("SERVICE_LOCATION") match {
				case Some("HOSTNAME") => serviceName + ":" + port.toString
				case Some("MARATHONHOST") => serviceName + ".marathon.mesos:" + port.toString
				case _ => "localhost:" + port
			}
			log.info("Resolving %s with %s", serviceName, hostPort)
			hostPort
		}

		val publisher = new KestrelQueuePublisher(resolve("kestrel", 22133), "timingevents")
		val registrationClient = Thrift.newIface[RegistrationService[Future]](resolve("registration", 6000))
		val resultsClient = Thrift.newIface[ResultsService[Future]](resolve("results", 7000))

		val gatewayService = new GatewayService(publisher, registrationClient, resultsClient)

		val server = Http.serve(":8080", gatewayService.router)
		Await.ready(server)
	}
}

