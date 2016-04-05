import java.util.concurrent.{ThreadPoolExecutor, RejectedExecutionHandler, ScheduledThreadPoolExecutor}

import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.finagle.Http
import com.twitter.finagle.util.HashedWheelTimer
import com.twitter.util.Await
import net.lag.kestrel.{LocalDirectory, PersistentQueue}
import net.lag.kestrel.config.QueueBuilder
import com.twitter.conversions.time._


// package finagletest {

	object Main extends App {

		def createQueue(name: String) : PersistentQueue = {
			val queueConfig = new QueueBuilder()
			val journalSyncScheduler =
				new ScheduledThreadPoolExecutor(
					Runtime.getRuntime.availableProcessors,
					new NamedPoolThreadFactory("journal-sync", true),
					new RejectedExecutionHandler {
						override def rejectedExecution(r: Runnable, executor: ThreadPoolExecutor) {
							// log.warning("Rejected journal fsync")
						}
					})
			val timer = HashedWheelTimer(100.milliseconds)
			val build = new QueueBuilder()
			val queue = new PersistentQueue(name, new LocalDirectory("/Users/tomv/.kestrel", journalSyncScheduler), build(), timer)
			queue.setup()
			queue
		}

		var eventQueue : PersistentQueue = createQueue("timingevents")

		val gatewayService = new GatewayService(eventQueue)

		val server = Http.serve(":8080", gatewayService.router)
		Await.ready(server)
	}
// }

