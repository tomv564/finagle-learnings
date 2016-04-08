import java.net.InetSocketAddress
import com.twitter.finagle.Thrift
import com.twitter.util.Await

import net.lag.kestrel.PersistentQueue
import scala.collection.mutable
import io.tomv.timing.common.LocalQueue

package io.tomv.timing.results {

	object Main extends App {

		val results = mutable.ArrayBuffer[Result]()

		val handler = new TimingEventHandler(results)
		val listener = new KestrelQueueListener("localhost:8000", "timingevents", handler)

		val thread = new Thread(listener).start()

		val service = new ResultsServiceImpl(results)
		val server = Thrift.serveIface(new InetSocketAddress(6000), service)
		Await.ready(server)

		listener.stop()

	}

}
