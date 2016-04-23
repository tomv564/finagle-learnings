import com.twitter.finagle.http.service.HttpResponseClassifier
import io.tomv.timing.registration.thrift.RegistrationService
import io.tomv.timing.results.thrift.ResultsService
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
import io.tomv.timing.registration.{Registration}
import io.tomv.timing.results._
import org.scalatest.concurrent.ScalaFutures

package io.tomv.timing.it {

  import com.fasterxml.jackson.databind.annotation.JsonDeserialize
  import io.tomv.timing.test.TwitterFutures
  import org.scalatest.concurrent.PatienceConfiguration.Timeout
  import org.scalatest.time.{Span, Seconds}

  case class ResultList(@JsonDeserialize(contentAs = classOf[Result]) value: Seq[Result])

  class IntegrationSuite extends FunSuite with ScalaFutures with TwitterFutures with BeforeAndAfterEach {
    var client: Service[Request, Response] = _
    val testRegistration = Registration("", "Run Rabbit, Run", "M2025")
    var registrationClient: RegistrationService[Future] = _
    var resultsClient: ResultsService[Future] = _

    implicit val asyncConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)))

    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    //    mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true)
//    val gatewayHost = "local.docker:8080"
    val gatewayHost = "node2:8080"

    override def beforeEach(): Unit = {
      client = Http.client.
        withResponseClassifier(HttpResponseClassifier.ServerErrorsAsFailures).
        newService(gatewayHost)
    }

    override def afterEach(): Unit = {
      Closable.all(client).close()
    }

    def generateRegistration(category: String) : Registration = {
      val name = "testperson-" + java.util.UUID.randomUUID.toString().substring(0, 6)
      Registration("", name, category)
    }


    def listRegistrations(client: Service[Request, Response]): Future[Seq[Registration]] = {
      val request = Request(Method.Get, "/registrations")
      client(request) map {
        r => r withReader {
          reader => mapper.readValue(reader, classOf[Seq[Registration]])
        }
      }
    }

    def listResults(client: Service[Request, Response]): Future[Seq[Result]] = {
      val request = Request(Method.Get, "/results")
      client(request) map {
        r => r withReader {
          reader => mapper.readValue(reader, classOf[Array[Result]])
        }
      }
    }

    def createRegistration(client: Service[Request, Response], registration: Registration): Future[Registration] = {
      val request = Request(Method.Post, "/registrations")
      val jsonString: String = mapper.writeValueAsString(registration)
      request.contentType = MediaType.Json
      request.contentString = jsonString
      client(request) map {
        r =>
          assert(r.status == Status.Created)
          r withReader {
            reader => mapper.readValue(reader, classOf[Registration])
          }
      }
    }

    def ChipStarted(chipNumber: String): TimingEvent = {
      TimingEvent(EventType.CHIP_START, System.currentTimeMillis, Some(chipNumber), None)
    }

    def ChipFinished(chipNumber: String): TimingEvent = {
      TimingEvent(EventType.CHIP_FINISH, System.currentTimeMillis, Some(chipNumber), None)
    }

    def createTimingEvent(client: Service[Request, Response], event: TimingEvent): Future[Boolean] = {
      val request = Request(Method.Post, "/events")
      val jsonString: String = mapper.writeValueAsString(event)
      request.contentType = MediaType.Json
      request.contentString = jsonString
      client(request) map {
        response => (response.status == Status.Created)
      }

    }

    test("Can list registrations") {

      whenReady (listRegistrations(client)) {
        list => assert(list.length >= 0)
      }

    }

    test("Can create registration") {

      var registrationCount = 0

      whenReady (listRegistrations(client)) {
        list => registrationCount = list.length
      }

      whenReady (createRegistration(client, testRegistration)) {
        reg => assert(!reg.chipNumber.isEmpty())

               registrationCount += 1
      }

      whenReady(listRegistrations(client)) {
        list => assert(list.length == registrationCount)
      }

    }


    test("Can list results") {

     whenReady(listResults(client)) {
       list => assert(list.length >= 0)
     }

    }

    test("can start race") {
      whenReady(createTimingEvent(client, ChipStarted(testRegistration.chipNumber))) {
        r => assert(r == true)
      }
    }

    test("can finish race") {
      whenReady(createTimingEvent(client, ChipFinished(testRegistration.chipNumber))) {
        r => assert(r == true)
      }
    }

    test("can have a result after a race") {

      var registration = generateRegistration("M2030")

      whenReady (createRegistration(client, registration)) {
        reg => assert(!reg.chipNumber.isEmpty())
                registration = reg
      }

      whenReady(createTimingEvent(client, ChipStarted(registration.chipNumber))) {
        r => assert(r == true)
      }

      whenReady(createTimingEvent(client, ChipFinished(registration.chipNumber))) {
        r => assert(r == true)
      }

      Thread.sleep(100)

      val results = listResults(client)
      whenReady(results, Timeout(Span(1, Seconds))) {
        list =>
          assert(list.nonEmpty)
          assert(list.exists(r => r.name == registration.name))
      }

    }


  }

}