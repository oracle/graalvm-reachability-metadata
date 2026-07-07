/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import java.util.Date

import akka.actor.ActorSystem
import akka.serialization.JavaSerializer
import akka.serialization.SerializationExtension
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class JavaSerializerTest {

  @Test
  def serializationExtensionRoundTripsSerializableObjectWithJavaSerializer(): Unit = {
    withActorSystem("java-serializer-round-trip") { system: ActorSystem =>
      val serialization = SerializationExtension(system)
      val payload: Date = new Date(123456789L)

      assertThat(serialization.findSerializerFor(payload)).isInstanceOf(classOf[JavaSerializer])

      val bytes: Array[Byte] = serialization.serialize(payload).get
      val restored: AnyRef = serialization
        .deserialize(bytes, serialization.findSerializerFor(payload).identifier, "")
        .get

      assertThat(restored).isEqualTo(payload)
    }
  }

  private def withActorSystem(name: String)(body: ActorSystem => Unit): Unit = {
    val config: Config = ConfigFactory.parseString("""
      akka.actor.allow-java-serialization = on
      akka.actor.warn-about-java-serializer-usage = off
      """)
    val system: ActorSystem = ActorSystem(name, config)
    try body(system)
    finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}
