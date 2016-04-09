import java.net.InetSocketAddress
import com.twitter.finagle.Thrift
import com.twitter.util.Await

import net.lag.kestrel.PersistentQueue
import scala.collection.mutable
import io.tomv.timing.common.LocalQueue

package io.tomv.timing.results {

	import com.twitter.util.Future
	import io.tomv.timing.registration.thrift.RegistrationService

	object Main extends App {

		val results = mutable.ArrayBuffer[Result]()

		val registrationClient = Thrift.newIface[RegistrationService[Future]]("localhost:6000")
		val handler = new ResultTimingEventHandler(results, registrationClient)
		val listener = new KestrelQueueListener("localhost:22133", "timingevents", handler)
//		val thread = new Thread(listener).start()
		val handle = listener.listen()

		val service = new ResultsServiceImpl(results)
		val server = Thrift.serveIface(new InetSocketAddress(7000), service)
		Await.ready(server)

		handle.close()
//		listener.stop()

	}

}
