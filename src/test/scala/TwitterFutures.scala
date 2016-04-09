import com.twitter.util.{Throw, Return}
import org.scalatest.concurrent.Futures

package io.tomv.timing.test {

  trait TwitterFutures extends Futures {

    import scala.language.implicitConversions

    implicit def convertTwitterFuture[T](twitterFuture: com.twitter.util.Future[T]): FutureConcept[T] =
      new FutureConcept[T] {
        override def eitherValue: Option[Either[Throwable, T]] = {
          twitterFuture.poll.map {
            case Return(o) => Right(o)
            case Throw(e) => Left(e)
          }
        }

        override def isCanceled: Boolean = false

        override def isExpired: Boolean = false
      }
  }

}
