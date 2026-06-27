/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.TypedActor
import akka.actor.TypedActor.MethodCall
import akka.actor.TypedActorExtension
import akka.actor.TypedProps
import akka.japi.Creator
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.util.function.{ Function => JavaFunction }
import java.util.function.Supplier
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class TypedActorInnerMethodCallTest {
  @Test
  def invokesTypedActorMethodCallsWithNullEmptyAndNonEmptyParameterArrays(): Unit = {
    implicit val timeout: Timeout = Timeout(10.seconds)
    val system: ActorSystem = ActorSystem("typed-actor-method-call", quietConfig)
    val typedActor: TypedActorExtension = TypedActor(system)

    try {
      val supplierProxy: Supplier[String] = typedActor.typedActorOf[Supplier[String], MethodCallTypedActorService](
        typedPropsFor(classOf[Supplier[_]].asInstanceOf[Class[_ >: MethodCallTypedActorService]]),
        "supplier")

      assertThat(supplierProxy.get()).isEqualTo("supplied-value")

      val supplierActor: ActorRef = typedActor.getActorRefFor(supplierProxy)
      val emptyParameterCall: MethodCall = MethodCall(classOf[Supplier[_]].getMethod("get"), Array.empty[AnyRef])
      val emptyParameterResponse: Any = Await.result(ask(supplierActor, emptyParameterCall), 10.seconds)

      assertThat(emptyParameterResponse).isEqualTo("supplied-value")

      val functionProxy: JavaFunction[String, String] =
        typedActor.typedActorOf[JavaFunction[String, String], MethodCallTypedActorService](
          typedPropsFor(classOf[JavaFunction[_, _]].asInstanceOf[Class[_ >: MethodCallTypedActorService]]),
          "function")

      assertThat(functionProxy.apply("argument")).isEqualTo("applied-argument")
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  private def typedPropsFor(interface: Class[_ >: MethodCallTypedActorService]): TypedProps[MethodCallTypedActorService] =
    new TypedProps[MethodCallTypedActorService](interface, MethodCallTypedActorServiceCreator)
      .withTimeout(Timeout(10.seconds))

  private def quietConfig: Config = ConfigFactory.parseString("""
      akka.loglevel = "ERROR"
      akka.stdout-loglevel = "ERROR"
      akka.actor.default-dispatcher.shutdown-timeout = 10s
      akka.actor.typed.timeout = 10s
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      """).withFallback(ConfigFactory.load())
}

object MethodCallTypedActorServiceCreator extends Creator[MethodCallTypedActorService] {
  override def create(): MethodCallTypedActorService = new MethodCallTypedActorService
}

final class MethodCallTypedActorService extends Supplier[String] with JavaFunction[String, String] {
  override def get(): String = "supplied-value"

  override def apply(value: String): String = s"applied-$value"
}
