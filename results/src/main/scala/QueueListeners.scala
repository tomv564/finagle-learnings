import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.kestrel.{ReadMessage, ReadHandle, Client}
import com.twitter.finagle.kestrel.protocol.Kestrel
import com.twitter.finagle.service.Backoff
import com.twitter.util.JavaTimer
import com.twitter.conversions.time._
import net.lag.kestrel.{PersistentQueue, QItem}
import org.apache.thrift.protocol.{TBinaryProtocol, TProtocol}
import org.apache.thrift.transport.TMemoryBuffer

package io.tomv.timing.results {

  import com.twitter.io.Buf

  class KestrelQueueListener(hostAndPort: String, queueName: String, handler: TimingEventHandler) extends Runnable {

    var running = false

    def stop(): Unit = {
      running = false
      handle.close()
    }

    var handle: ReadHandle = _

    override def run(): Unit = {
      handle = listen()
      handle.wait()
    }

    def read() = {
      val client = Client(ClientBuilder()
        .codec(Kestrel())
        .hosts(hostAndPort)
        .hostConnectionLimit(1) // process at most 1 item per connection concurrently
        .buildFactory())

      client.get(queueName)

    }

    def listen(): ReadHandle = {
      running = true
      val client = Client(ClientBuilder()
        .codec(Kestrel())
        .hosts(hostAndPort)
        .hostConnectionLimit(1) // process at most 1 item per connection concurrently
        .buildFactory())


      val timer = new JavaTimer(isDaemon = true)
      val retryBackoffs = Backoff.const(10.milliseconds)
      val readHandle = client.readReliably(queueName, timer, retryBackoffs)

      // Attach an async error handler that prints to stderr
      readHandle.error foreach { e =>
        if (running) System.err.println("zomg! got an error " + e)
      }

      // Attach an async message handler that prints the messages to stdout
      readHandle.messages foreach { msg =>
        try {
          readMessage(msg)
        } finally {
          msg.ack.sync() // if we don't do this, no more msgs will come to us
        }
      }

      readHandle

    }

    def readMessage(msg: ReadMessage): Unit = {
      val bytes = new Array[Byte](msg.bytes.length)
      msg.bytes.write(bytes, 0)
      val buffer = new TMemoryBuffer(512)
      buffer.write(bytes, 0, bytes.length)
      val prot = new TBinaryProtocol(buffer)
      val event = thrift.TimingEvent.decode(prot)
      handler.handleEvent(event)
    }

  }

  class PersistentQueueListener(queue: PersistentQueue, handler: TimingEventHandler) extends Runnable {

    val timeout = 250.milliseconds.fromNow

    @volatile
    var stopped: Boolean = false

    def run = {
      stopped = false
      listen()
    }

    def stop(): Unit = {
      stopped = true
    }

    def listen(): Unit = {
      while (!stopped) {
        queue.waitRemove(Some(timeout), false).onSuccess { item =>
          item.foreach { e =>
            handleQItem(e)
          }
        }
      }
    }

    def handleQItem(item: QItem): Unit = {
      val prot = createProtocol(item.data)
      val event = thrift.TimingEvent.decode(prot)
      handler.handleEvent(event)
    }

    def createProtocol(bytes: Array[Byte]): TProtocol = {
      val buffer = new TMemoryBuffer(512)
      buffer.write(bytes, 0, bytes.length)
      new TBinaryProtocol(buffer)
    }


  }

}