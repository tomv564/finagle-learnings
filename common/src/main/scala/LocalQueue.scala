import com.twitter.finagle.util.HashedWheelTimer
import net.lag.kestrel.{PersistentQueue, LocalDirectory}
import net.lag.kestrel.config.{QueueConfig, QueueBuilder}
import com.twitter.conversions.time._
import com.twitter.concurrent.NamedPoolThreadFactory
import java.util.concurrent._
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.transport.TMemoryBuffer
import java.util.Arrays
import com.twitter.scrooge.ThriftStruct
import org.apache.thrift.protocol._

package io.tomv.timing.common {

  object LocalQueue {

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

  }
}