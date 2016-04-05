import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfterEach
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.Thrift
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.util.{Closable, Future}
import com.twitter.finagle.http.Method
import com.twitter.util.Await
import com.fasterxml.jackson.databind.{ObjectMapper, DeserializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import scala.util.{Try, Success, Failure}
import com.twitter.finagle.http.MediaType
import io.tomv.timing.registration.{Registration, RegistrationServiceImpl}
import io.tomv.timing.results.thrift.{Result, TimingEvent}
import io.tomv.timing.results.{EventType}
// import scala.reflect.ClassTag
//import java.net.InetSocketAddress

// import scala.reflect._
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

class QueueSuite extends FunSuite {

  val testRegistration = Registration("AB1234", "Run Rabbit, Run", "M2025")

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

  def writeBytes(item: ThriftStruct) : Array[Byte] = {
      val buffer = new TMemoryBuffer(512)
      val protocol = new TBinaryProtocol(buffer)
      item.write(protocol)

      Arrays.copyOfRange(buffer.getArray(), 0, buffer.length())
  }

  def createProtocol(bytes: Array[Byte]) : TProtocol = {
    val buffer = new TMemoryBuffer(512)
    buffer.write(bytes, 0, bytes.length)
    new TBinaryProtocol(buffer)
  }

  def ChipStarted(chipNumber: String): TimingEvent = {
    TimingEvent(EventType.CHIP_START, System.currentTimeMillis, Some(chipNumber), None)
  }

  test("Can create queue") {

    val queue = createQueue("test")
    assert(queue.peek() == None)
    queue.close()
    assert(queue.isClosed)
  }

  test("Can post and read bytes to queue") {

    val queue = createQueue("bytes")
    queue.add("hello".getBytes)

    val item = queue.peek()
    assert(item.isDefined)

    val read = queue.remove()
    val content = new String(read.get.data)
    assert(content == "hello")

    assert(queue.peek() == None)
    queue.close()

  }

  test("can post and read thrift models to queue") {
    val queue = createQueue("bytes")
    val started = ChipStarted(testRegistration.chipNumber)
    queue.add(writeBytes(started))

    val item = queue.peek()
    assert(item.isDefined)

    val read = queue.remove()

    val prot = createProtocol(read.get.data)
    val obj = TimingEvent.decode(prot)
    // val content = new String(read.get.data)
    assert(started == obj)

    assert(queue.peek() == None)
    queue.close()

  }

}