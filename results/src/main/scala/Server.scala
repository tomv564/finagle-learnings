import java.net.InetSocketAddress
import com.twitter.finagle.Thrift
import com.twitter.util.Await

package io.tomv.timing.results {

	object Main extends App {
		val server = Thrift.serveIface(new InetSocketAddress(6000), new ResultsServiceImpl())
		Await.ready(server)
	}

}
