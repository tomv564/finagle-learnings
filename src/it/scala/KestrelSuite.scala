import com.twitter.finagle.kestrel.protocol.Stored
import com.twitter.io.Buf
import io.tomv.timing.KestrelQueuePublisher
import io.tomv.timing.results._
import io.tomv.timing.test.TwitterFutures
import org.scalatest.FunSuite
import io.tomv.timing.registration.{Registration, RegistrationServiceImpl}

import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.transport.TMemoryBuffer
import java.util.Arrays
import com.twitter.scrooge.ThriftStruct
import org.apache.thrift.protocol._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import com.twitter.conversions.time
import org.scalatest.time.{Span, Seconds}

import scala.collection.mutable

class TimingEventCollector extends TimingEventHandler {

  val events = mutable.MutableList[thrift.TimingEvent]()

  override def handleEvent(event: thrift.TimingEvent): Unit = {
    events += event
  }
}


class KestrelSuite extends FunSuite with ScalaFutures with TwitterFutures {

  val testRegistration = Registration("AB1234", "Run Rabbit, Run", "M2025")


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

  test("Can read a queue") {
    val listener = new KestrelQueueListener("local.docker:22133", "timingevents", null)
    whenReady(listener.read()) {
      r => assert(r != null)
    }
  }


  test("Can listen to queue") {
    val collector = new TimingEventCollector()
    val listener = new KestrelQueueListener("local.docker:22133", "timingevents", collector)
    val handle = listener.listen()
    Thread.sleep(1000)
    handle.close()
  }

  test("Can write/read to a queue") {

    val publisher = new KestrelQueuePublisher("local.docker:22133", "timingevents")
    val publish = publisher.publish(ChipStarted("ABCDD"))
    whenReady(publish, Timeout(Span(2, Seconds))) {
      response => assert(response == Stored())
    }

    Thread.sleep(1000)

    val listener = new KestrelQueueListener("local.docker:22133", "timingevents", null)
    whenReady(listener.read()) {
      r => assert(r.isDefined)
        val Buf.Utf8(str) = r.get
        println(str)
    }

  }

  test("Can listen to a queue while writing") {
    val collector = new TimingEventCollector()

    val listener = new KestrelQueueListener("local.docker:22133", "timingevents", collector)
    val handle = listener.listen()

    val publisher = new KestrelQueuePublisher("local.docker:22133", "timingevents")
    val publish = publisher.publish(ChipStarted("ABCDD"))
    whenReady(publish, Timeout(Span(2, Seconds))) {
      response => assert(response == Stored())
    }

    Thread.sleep(1000)

    handle.close()

    assert(collector.events.length > 0)

  }
//
//  test("Can post and read bytes to queue") {
//
//    val queue = LocalQueue.createQueue("bytes")
//    queue.add("hello".getBytes)
//
//    val item = queue.peek()
//    assert(item.isDefined)
//
//    val read = queue.remove()
//    val content = new String(read.get.data)
//    assert(content == "hello")
//
//    assert(queue.peek() == None)
//    queue.close()
//
//  }
//
//  test("can post and read thrift models to queue") {
//    val queue = LocalQueue.createQueue("bytes")
//    val started = ChipStarted(testRegistration.chipNumber)
//    queue.add(writeBytes(started))
//
//    val item = queue.peek()
//    assert(item.isDefined)
//
//    val read = queue.remove()
//
//    val prot = createProtocol(read.get.data)
//    val obj = TimingEvent.decode(prot)
//    // val content = new String(read.get.data)
//    assert(started == obj)
//
//    assert(queue.peek() == None)
//    queue.close()
//
//  }

}