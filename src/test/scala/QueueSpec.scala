import io.tomv.timing.results.EventType
import org.scalatest.FunSuite
import io.tomv.timing.registration.{Registration, RegistrationServiceImpl}
import io.tomv.timing.results.thrift.{Result, TimingEvent}

import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.transport.TMemoryBuffer
import java.util.Arrays
import com.twitter.scrooge.ThriftStruct
import org.apache.thrift.protocol._


import io.tomv.timing.common.LocalQueue

class QueueSuite extends FunSuite {

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

  test("Can create queue") {

    val queue = LocalQueue.createQueue("test")
    assert(queue.peek() == None)
    queue.close()
    assert(queue.isClosed)
  }

  test("Can post and read bytes to queue") {

    val queue = LocalQueue.createQueue("bytes")
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
    val queue = LocalQueue.createQueue("bytes")
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