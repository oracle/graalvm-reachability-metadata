/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import akka.actor.ActorSystem
import akka.serialization.JavaSerializer
import akka.serialization.Serialization
import akka.serialization.SerializationExtension
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.SerialVersionUID
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class JavaSerializerTest {
  @Test
  def roundTripsSerializableMessageThroughAkkaSerializationExtension(): Unit = {
    val system: ActorSystem = ActorSystem("java-serializer-round-trip", javaSerializationConfig)

    try {
      val serialization: Serialization = SerializationExtension(system)
      val message: JavaSerializerRoundTripMessage = new JavaSerializerRoundTripMessage("payload", 42)

      assertThat(serialization.findSerializerFor(message)).isInstanceOf(classOf[JavaSerializer])

      val bytes: Array[Byte] = serialization.serialize(message).get
      val roundTripped: JavaSerializerRoundTripMessage =
        serialization.deserialize(bytes, classOf[JavaSerializerRoundTripMessage]).get

      assertThat(bytes).isNotEmpty()
      assertThat(roundTripped).isEqualTo(message)
      assertThat(roundTripped).isNotSameAs(message)
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

@SerialVersionUID(1L)
final class JavaSerializerRoundTripMessage(val text: String, val number: Int) extends Serializable {
  override def equals(other: Any): Boolean = other match {
    case that: JavaSerializerRoundTripMessage => text == that.text && number == that.number
    case _                                    => false
  }

  override def hashCode(): Int = 31 * text.hashCode + number

  override def toString: String = s"JavaSerializerRoundTripMessage($text,$number)"
}
