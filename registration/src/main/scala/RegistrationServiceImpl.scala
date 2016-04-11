import com.twitter.util.Future
import scala.collection.mutable

package io.tomv.timing.registration {

	import com.twitter.logging.Logger

	case class Registration(chipNumber: String, name: String, category: String) extends thrift.Registration

	class RegistrationServiceImpl extends thrift.RegistrationService[Future] {

		private val log = Logger.get(getClass)
		val registrations = mutable.MutableList[thrift.Registration]()

		def isValidRegistration(reg: Registration): Boolean =
			(!reg.name.isEmpty() && !reg.chipNumber.isEmpty() && !reg.category.isEmpty())

	    override def create(name: String, category: String): Future[thrift.Registration] = {
	    	val newChipNumber = java.util.UUID.randomUUID.toString().substring(0, 6)
	    	val registration = new Registration(newChipNumber, name, category)
	    	registrations += registration
				log.info("Stored registration %s", newChipNumber)
	    	Future.value(registration)
	    }

	    override def get(chipNumber: String): Future[thrift.Registration] = {
	    	registrations.find(r => r.chipNumber == chipNumber) match {
		    	case Some(reg) => {
						log.info("Returning registration for chip %s", chipNumber)
						Future.value(reg)
					}
		    	case None => {
						log.error("Could not find registration for chip %s", chipNumber)
						Future.exception(new thrift.RegistrationException("asdf"))
					}
		    }
		}

	    override def getAll(): Future[Seq[thrift.Registration]] = {
				log.info("Returning all registrations")
				Future.value(registrations)
			}
	}

}