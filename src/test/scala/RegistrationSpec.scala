import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfterEach
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.util.Closable
import com.twitter.finagle.http.Method
import com.twitter.util.Await
import com.fasterxml.jackson.databind.{ObjectMapper, DeserializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import scala.util.{Try, Success, Failure}

class RegistrationSuite extends FunSuite with BeforeAndAfterEach {
  var server: com.twitter.finagle.ListeningServer = _
  var client: Service[Request, Response] = _
  val mapper = new ObjectMapper()
	mapper.registerModule(DefaultScalaModule)
	mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true)

  override def beforeEach(): Unit = {
	server = Http.serve(":8080", RegistrationService.router)
    client = Http.client.newService(":8080")
  }

  override def afterEach(): Unit = {
	Closable.all(server, client).close()
  }

  test("Can call RegistrationService") {

  	val request = Request(Method.Get, "/")
  	val response = client(request)
  	response.onSuccess(r => {
  			assert(r.status == Status.Ok)
  		})
  	response.onFailure(r => fail(r.toString()))
  	Await.result(response)

  }

  test("Can list registrations") {

  	val request = Request(Method.Get, "/registrations")

  	val response = client(request)
  	response.onSuccess(r => {
  			assert(r.status == Status.Ok)
  			print(r.contentString)
  			r withReader {
  				reader => {
  					val list = mapper.readValue(reader, classOf[List[Registration]])
					print(list.length)
					assert(list.length == 1)
  				}
  			}
  		})
  	response.onFailure(r => fail(r.toString()))
  	Await.result(response)

  }

}