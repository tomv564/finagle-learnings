import com.twitter.util.Future
import scala.collection.mutable

package io.tomv.timing.registration {


	case class Registration(chipNumber: String, name: String, category: String) extends thrift.Registration

	class RegistrationServiceImpl extends thrift.RegistrationService[Future] {

		val testPerson = thrift.Registration("AB1234", "Test Person", "M3040")
		val registrations = mutable.MutableList[thrift.Registration](testPerson)

		def isValidRegistration(reg: Registration): Boolean =
			(!reg.name.isEmpty() && !reg.chipNumber.isEmpty() && !reg.category.isEmpty())


	    override def create(name: String, category: String): Future[thrift.Registration] = {
	    	val newChipNumber = java.util.UUID.randomUUID.toString().substring(0, 6)
	    	val registration = new Registration(newChipNumber, name, category)
	    	registrations += registration
	    	Future.value(registration)
	    }

	    override def get(chipNumber: String): Future[thrift.Registration] = {
	    	registrations.find(r => r.chipNumber == chipNumber) match {
		    	case Some(reg) => Future.value(reg)
		    	case _ => Future.exception(new thrift.RegistrationException("asdf"))
		    }
		}

	    override def getAll(): Future[Seq[thrift.Registration]] = Future.value(registrations)
	}

}