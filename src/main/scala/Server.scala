import com.twitter.finagle.Http
import com.twitter.util.Await
import net.lag.kestrel.PersistentQueue
import io.tomv.timing.common.LocalQueue


package io.tomv.timing.gateway {

	object Main extends App {

		var eventQueue : PersistentQueue = LocalQueue.createQueue("timingevents")

		val gatewayService = new GatewayService(eventQueue)

		val server = Http.serve(":8080", gatewayService.router)
		Await.ready(server)
	}
}

