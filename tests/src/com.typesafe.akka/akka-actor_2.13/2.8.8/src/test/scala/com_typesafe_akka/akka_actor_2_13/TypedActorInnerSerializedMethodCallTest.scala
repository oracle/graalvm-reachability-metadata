/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import java.lang.reflect.Method

import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.TypedActor.MethodCall
import akka.serialization.JavaSerializer
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class TypedActorInnerSerializedMethodCallTest {
  @Test
  def deserializesMethodCallThroughJavaSerializer(): Unit = {
    val config: Config = ConfigFactory.parseString("akka.actor.allow-java-serialization = on")

    withActorSystem("typed-actor-serialized-method-call", config) { system: ExtendedActorSystem =>
      val serializer: JavaSerializer = new JavaSerializer(system)
      val method: Method = classOf[SerializedMethodCallTarget].getMethod("message", classOf[String])
      val call: MethodCall = MethodCall(method, Array[AnyRef]("resolved"))

      val bytes: Array[Byte] = serializer.toBinary(call)
      val deserialized: AnyRef = serializer.fromBinary(bytes, None)

      val resolvedCall: MethodCall = deserialized.asInstanceOf[MethodCall]
      val target: SerializedMethodCallTarget = new SerializedMethodCallTarget()
      assertThat(resolvedCall(target)).isEqualTo("message: resolved")
    }
  }

  private def withActorSystem(name: String, config: Config)(body: ExtendedActorSystem => Unit): Unit = {
    val system: ActorSystem = ActorSystem(name, config)
    try {
      body(system.asInstanceOf[ExtendedActorSystem])
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}

class SerializedMethodCallTarget {
  def message(value: String): String = s"message: $value"
}
