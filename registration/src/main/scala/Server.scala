import java.net.InetSocketAddress
import com.twitter.finagle.Thrift
import com.twitter.util.Await

package io.tomv.timing.registration {

	import com.twitter.logging.{ConsoleHandler, Level, LoggerFactory}

	object Main extends App {

		val factory = LoggerFactory(
			node = "",
			level = Some(Level.INFO),
			handlers = List(
				ConsoleHandler()
			)
		)

		factory()

		val server = Thrift.serveIface(new InetSocketAddress(6000), new RegistrationServiceImpl())
		Await.ready(server)
	}

}

