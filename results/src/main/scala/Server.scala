import java.net.InetSocketAddress
import com.twitter.finagle.Thrift
import com.twitter.util.Await

import net.lag.kestrel.PersistentQueue
import scala.collection.mutable
import io.tomv.timing.common.LocalQueue

package io.tomv.timing.results {

	import com.twitter.logging.{ConsoleHandler, Level, LoggerFactory, Logger}
	import com.twitter.util.Future
	import io.tomv.timing.registration.thrift.RegistrationService

	object Main extends App {

		val factory = LoggerFactory(
			node = "",
			level = Some(Level.INFO),
			handlers = List(
				ConsoleHandler()
			)
		)

		factory()

		private val log = Logger.get(getClass)

		def resolve(serviceName: String, port: Int) : String = {
			val hostPort = sys.env.get("SERVICE_LOCATION") match {
				case Some("HOSTNAME") => serviceName + ":" + port.toString
				case _ => "localhost:" + port
			}
			log.info("Resolving %s with %s", serviceName, hostPort)
			hostPort
		}

		val results = mutable.ArrayBuffer[Result]()

		val registrationClient = Thrift.newIface[RegistrationService[Future]](resolve("registration", 6000))
		val handler = new ResultTimingEventHandler(results, registrationClient)

		log.info("Starting listener for timing events")
		val listener = new KestrelQueueListener(resolve("kestrel", 22133), "timingevents", handler)
		val handle = listener.listen()

		val service = new ResultsServiceImpl(results)
		val server = Thrift.serveIface(new InetSocketAddress(7000), service)
		Await.ready(server)

		log.info("Closing read handle for timing events")
		handle.close()

	}

}
