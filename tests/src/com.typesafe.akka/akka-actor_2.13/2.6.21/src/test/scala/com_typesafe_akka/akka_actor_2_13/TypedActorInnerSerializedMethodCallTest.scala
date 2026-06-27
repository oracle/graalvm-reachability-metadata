/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import akka.actor.ActorSystem
import akka.actor.TypedActor.MethodCall
import akka.serialization.JavaSerializer
import akka.serialization.Serialization
import akka.serialization.SerializationExtension
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.util.function.{ Function => JavaFunction }
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class TypedActorInnerSerializedMethodCallTest {
  @Test
  def roundTripsTypedActorMethodCallThroughAkkaJavaSerialization(): Unit = {
    val system: ActorSystem = ActorSystem("typed-actor-serialized-method-call", javaSerializationConfig)

    try {
      val serialization: Serialization = SerializationExtension(system)
      val methodCall: MethodCall = MethodCall(
        classOf[JavaFunction[_, _]].getMethod("apply", classOf[Object]),
        Array[AnyRef]("payload"))

      assertThat(serialization.findSerializerFor(methodCall)).isInstanceOf(classOf[JavaSerializer])

      val bytes: Array[Byte] = serialization.serialize(methodCall).get
      val roundTripped: MethodCall = serialization.deserialize(bytes, classOf[MethodCall]).get
      val service: JavaFunction[String, String] = new SerializedMethodCallService

      assertThat(bytes).isNotEmpty()
      assertThat(roundTripped.method.getDeclaringClass).isEqualTo(classOf[JavaFunction[_, _]])
      assertThat(roundTripped.method.getName).isEqualTo("apply")
      assertThat(roundTripped.parameters).containsExactly("payload")
      assertThat(roundTripped(service)).isEqualTo("serialized-payload")
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  private def javaSerializationConfig: Config = ConfigFactory.parseString("""
      akka.loglevel = "ERROR"
      akka.stdout-loglevel = "ERROR"
      akka.actor.default-dispatcher.shutdown-timeout = 10s
      akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
      akka.actor.allow-java-serialization = on
      akka.actor.warn-about-java-serializer-usage = off
      """).withFallback(ConfigFactory.load())
}

final class SerializedMethodCallService extends JavaFunction[String, String] {
  override def apply(value: String): String = s"serialized-$value"
}
