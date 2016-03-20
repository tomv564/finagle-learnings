import io.tomv.timing.registration.thrift.{Registration, RegistrationService, RegistrationException}
import com.twitter.util.Future
import scala.collection.mutable

package io.tomv.timing.registration {

	class RegistrationServiceImpl extends thrift.RegistrationService[Future] {

		val testPerson = thrift.Registration("AB1234", "Test Person", "M3040")
		val registrations = mutable.MutableList[thrift.Registration](testPerson)

		def isValidRegistration(reg: Registration): Boolean =
			(!reg.name.isEmpty() && !reg.chipNumber.isEmpty() && !reg.category.isEmpty())


	    override def create(registration: thrift.Registration): Future[Unit] = {
	    	registrations += registration
	    	Future.value(())
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