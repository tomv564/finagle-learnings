import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfterEach
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.util.{Closable, Future}
import com.twitter.finagle.http.Method
import com.twitter.util.Await
import com.fasterxml.jackson.databind.{ObjectMapper, DeserializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import scala.util.{Try, Success, Failure}
import com.twitter.finagle.http.MediaType
// import scala.reflect.ClassTag
// import scala.reflect._

class GatewaySuite extends FunSuite with BeforeAndAfterEach {
  var server: com.twitter.finagle.ListeningServer = _
  var client: Service[Request, Response] = _
  val testRegistration = Registration("AB1234", "Run Rabbit, Run", "M2025")

  val mapper = new ObjectMapper()
	mapper.registerModule(DefaultScalaModule)
	mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true)

  override def beforeEach(): Unit = {
	server = Http.serve(":8080", GatewayService.router)
    client = Http.client.newService(":8080")
  }

  override def afterEach(): Unit = {
	Closable.all(server, client).close()
  }

  // def getJson[T: ClassTag](client: Service[Request, Response], path: String): Future[T] = {
  // 	val request = Request(Method.Get, path)
  // 	client(request) map {
  // 		r => r withReader {
  // 			reader => mapper.readValue(reader, ???)
  // 		}
  // 	}
  // }

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

  test("Can call GatewayService") {

  	val request = Request(Method.Get, "/")
  	val response = client(request)
  	response.onSuccess(r => {
  			assert(r.status == Status.Ok)
  		})
  	response.onFailure(r => fail(r.toString()))
  	Await.result(response)

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

 //  test("Can list results") {
 //  	val request = Request(Method.Get, "/results")

 //  	val response = client(request)
 //  	response.onSuccess(r => {
 //  			assert(r.status == Status.Ok)
 //  			r withReader {
 //  				reader => {
 //  					val list = mapper.readValue(reader, classOf[List[String]])
 //  					print(list.length)
 //  					assert(list.length == 0)
 //  				}
 //  			}
 //  		})
	// response.onFailure(r => fail(r.toString()))
	// Await.result(response)
 //  }

  // test("Can send timing events") {
  // 	val request = Request(Method.Post, "/event")
  // 	request.contentType = MediaType.Json
  // 	val response = client(request)
  // 	response.onSuccess(r => {
  // 			assert(r.status == Status.Created)
  // 			print(r.contentString)
  // 		})
  // 	response.onFailure(r => fail(r.toString()))
  // 	Await.result(response)
  // }

}