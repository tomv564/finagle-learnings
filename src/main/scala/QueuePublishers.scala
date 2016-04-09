import io.tomv.timing.results.TimingEvent

package io.tomv.timing {

  import java.util.Arrays

  import com.twitter.finagle.builder.ClientBuilder
  import com.twitter.finagle.kestrel.Client
  import com.twitter.finagle.kestrel.protocol.Kestrel
  import com.twitter.io.Buf
  import net.lag.kestrel.PersistentQueue
  import org.apache.thrift.protocol.TBinaryProtocol
  import org.apache.thrift.transport.TMemoryBuffer

  trait QueuePublisher {
    def publish(event: TimingEvent): Unit
  }

  class PersistentQueuePublisher(queue: PersistentQueue) extends QueuePublisher {
    override def publish(event: TimingEvent): Unit = {

      val buffer = new TMemoryBuffer(512)
      val protocol = new TBinaryProtocol(buffer)
      event.write(protocol)

      val bytes = Arrays.copyOfRange(buffer.getArray(), 0, buffer.length())
      queue.add(bytes, None)

    }
  }

  class KestrelQueuePublisher(hostAndPort: String, queueName: String) extends QueuePublisher {

    // use Client.makeThrift to use thrift protocol
    val client = Client(ClientBuilder()
      .codec(Kestrel())
      .hosts(hostAndPort)
      .hostConnectionLimit(1) // process at most 1 item per connection concurrently
      .buildFactory())

    override def publish(event: TimingEvent): Unit = {
      val buffer = new TMemoryBuffer(512)
      val protocol = new TBinaryProtocol(buffer)
      event.write(protocol)

      val bytes = Arrays.copyOfRange(buffer.getArray(), 0, buffer.length())
      client.set(queueName, Buf.ByteArray.Owned.apply(buffer.getArray(), 0, buffer.length()))
    }

  }

}