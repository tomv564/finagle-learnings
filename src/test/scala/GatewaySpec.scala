import com.twitter.finagle.http.service.HttpResponseClassifier
import io.tomv.timing.registration.thrift.RegistrationService
import io.tomv.timing.results.thrift.ResultsService
import io.tomv.timing.{PersistentQueuePublisher, GatewayService}
import io.tomv.timing.GatewayService
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfterEach
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.Thrift
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.{Closable, Future}
import com.twitter.finagle.http.Method
import com.twitter.util.Await
import com.fasterxml.jackson.databind.{ObjectMapper, DeserializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.http.MediaType
import io.tomv.timing.registration.{Registration, RegistrationServiceImpl}
import io.tomv.timing.results._
import org.scalatest.concurrent.ScalaFutures
import scala.collection.mutable.ArrayBuffer

import io.tomv.timing.common.LocalQueue

import net.lag.kestrel.PersistentQueue

class GatewaySuite extends FunSuite with ScalaFutures with TwitterFutures with BeforeAndAfterEach {
  var gatewayServer: com.twitter.finagle.ListeningServer = _
  var registrationServer: com.twitter.finagle.ListeningServer = _
  var resultsServer: com.twitter.finagle.ListeningServer = _
	var eventHandler: TimingEventHandler = _
  var queueListener: PersistentQueueListener = _
	var queuePublisher: PersistentQueuePublisher = _
  var queue: PersistentQueue = _
  val results : ArrayBuffer[Result] = new ArrayBuffer[Result]()
  var client: Service[Request, Response] = _
  val testRegistration = Registration("AB1234", "Run Rabbit, Run", "M2025")
	var registrationClient: RegistrationService[Future] = _
	var resultsClient: ResultsService[Future] = _

  val mapper = new ObjectMapper()
	mapper.registerModule(DefaultScalaModule)
	mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true)

  override def beforeEach(): Unit = {
    registrationServer = Thrift.serveIface(":6000", new RegistrationServiceImpl())
    resultsServer = Thrift.serveIface(":7000", new ResultsServiceImpl(results))
		val registrationClient = Thrift.newIface[RegistrationService[Future]]("localhost:6000")
		val resultsClient = Thrift.newIface[ResultsService[Future]]("localhost:6001")

		queue = LocalQueue.createQueue("timingevents")
		eventHandler = new TimingEventHandler(results, registrationClient)
		queueListener = new PersistentQueueListener(queue, eventHandler)
		queuePublisher = new PersistentQueuePublisher(queue)
	  gatewayServer = Http.serve(":8080", new GatewayService(queuePublisher, registrationClient, resultsClient).router)
    client = Http.client.withResponseClassifier(HttpResponseClassifier.ServerErrorsAsFailures).newService(":8080")
  }

  override def afterEach(): Unit = {
	   Closable.all(registrationServer, resultsServer, gatewayServer, client).close()
  }

  def listRegistrations(client: Service[Request, Response]): Future[List[Registration]] = {
  	val request = Request(Method.Get, "/registrations")
		client(request) map {
      r => r withReader {
  			reader => mapper.readValue(reader, classOf[List[Registration]])
  		}
  	}
  }

  def listResults(client: Service[Request, Response]): Future[List[Result]] = {
  	val request = Request(Method.Get, "/results")
  	client(request) map {
  		r => r withReader {
  			reader => mapper.readValue(reader, classOf[List[Result]])
  		}
  	}
  }

  def createRegistration(client: Service[Request, Response], registration: Registration) : Future[Unit] = {
  	val request = Request(Method.Post, "/registrations")
  	val jsonString: String = mapper.writeValueAsString(testRegistration)
  	request.contentType = MediaType.Json
  	request.contentString = jsonString
	client(request) map {
		response => assert(response.status == Status.Created)
	}
  }

  def ChipStarted(chipNumber: String): TimingEvent = {
  	TimingEvent(EventType.CHIP_START, System.currentTimeMillis, Some(chipNumber), None)
  }

  def ChipFinished(chipNumber: String): TimingEvent = {
  	TimingEvent(EventType.CHIP_FINISH, System.currentTimeMillis, Some(chipNumber), None)
  }

  def createTimingEvent(client: Service[Request, Response], event: TimingEvent) : Future[Unit] = {
  	val request = Request(Method.Post, "/events")
  	val jsonString: String = mapper.writeValueAsString(event)
  	request.contentType = MediaType.Json
  	request.contentString = jsonString
	client(request) map {
		response => assert(response.status == Status.Created)
	}

  }

  test("Can list registrations") {

  	val registrations = listRegistrations(client)
  	registrations.onFailure(t => fail(t.toString))
  	registrations.onSuccess(
  		list => assert(list.length == 1)
  		)
  	Await.result(registrations)

  }

  test("Can create registration") {

  	val created = createRegistration(client, testRegistration)
  	created.onFailure(t => fail(t.toString))
  	Await.result(created)

  	val registrations = listRegistrations(client)
  	registrations.onFailure(t => fail(t.toString))
  	registrations.onSuccess(
  		list => assert(list.length == 2)
  		)
  	Await.result(registrations)

  }


  test("Can list results") {

  	val results = listResults(client)
  	results.onFailure(t => fail(t.toString))
  	results.onSuccess(
  		list => assert(list.length == 0)
  		)
  	Await.result(results)

  }

  test("can start race") {
  	val created = createTimingEvent(client, ChipStarted(testRegistration.chipNumber))
  	created.onFailure(t => fail(t.toString))
  	Await.result(created)
  }

  test("can finish race") {
  	val created = createTimingEvent(client, ChipFinished(testRegistration.chipNumber))
  	created.onFailure(t => fail(t.toString))
  	Await.result(created)
  }

  test("can have a result after a race") {

		val thread = new Thread(queueListener).start()

  	val created = createRegistration(client, testRegistration)
  	created.onFailure(t => fail(t.toString))
  	Await.result(created)

  	val started = createTimingEvent(client, ChipStarted(testRegistration.chipNumber))
  	started.onFailure(t => fail(t.toString))
  	Await.result(started)

  	val finished = createTimingEvent(client, ChipFinished(testRegistration.chipNumber))
  	finished.onFailure(t => fail(t.toString))
  	Await.result(finished)

    Thread.sleep(100)

  	val results = listResults(client)
		whenReady (results) {
			list =>
				assert(!list.isEmpty)
				queueListener.stop()
		}

  }


}

import com.twitter.util.{Throw, Return}
import org.scalatest.concurrent.Futures

trait TwitterFutures extends Futures {

	import scala.language.implicitConversions

	implicit def convertTwitterFuture[T](twitterFuture: com.twitter.util.Future[T]): FutureConcept[T] =
		new FutureConcept[T] {
			override def eitherValue: Option[Either[Throwable, T]] = {
				twitterFuture.poll.map {
					case Return(o) => Right(o)
					case Throw(e)  => Left(e)
				}
			}
			override def isCanceled: Boolean = false
			override def isExpired: Boolean = false
		}
}