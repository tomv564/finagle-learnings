import java.net.InetSocketAddress
import com.twitter.finagle.Thrift
import com.twitter.util.Await

package io.tomv.timing.registration {

	object Main extends App {
		val server = Thrift.serveIface(new InetSocketAddress(6000), new RegistrationServiceImpl())
		Await.ready(server)
	}

}

