import java.net.InetSocketAddress
import com.twitter.finagle.Thrift
import com.twitter.util.Await

import net.lag.kestrel.PersistentQueue
import scala.collection.mutable
import io.tomv.timing.common.LocalQueue

package io.tomv.timing.results {

	object Main extends App {

		var eventQueue : PersistentQueue = LocalQueue.createQueue("timingevents")
		val results = mutable.ArrayBuffer[Result]()

		val listener = new TimingEventListener(eventQueue, results)

		val service = new ResultsServiceImpl(results)
		val server = Thrift.serveIface(new InetSocketAddress(6000), service)
		Await.ready(server)
	}

}
