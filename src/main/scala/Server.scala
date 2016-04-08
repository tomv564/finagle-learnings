import com.twitter.finagle.Http
import com.twitter.util.Await
import net.lag.kestrel.PersistentQueue
import io.tomv.timing.common.LocalQueue


package io.tomv.timing {

	object Main extends App {

		val gatewayService = new GatewayService(new KestrelQueuePublisher("localhost:8000", "timingevents"))

		val server = Http.serve(":8080", gatewayService.router)
		Await.ready(server)
	}
}

