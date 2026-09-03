/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.serialization.JavaSerializer
import org.apache.pekko.serialization.Serialization
import org.apache.pekko.serialization.SerializationExtension
import org.apache.pekko.serialization.Serializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class JavaSerializerTest {
  @Test
  def serializesAndDeserializesSerializableMessagesThroughSerializationExtension(): Unit = {
    val config: Config = ConfigFactory.parseString("""
        pekko.actor.allow-java-serialization = on
        pekko.actor.warn-about-java-serializer-usage = off
        """)
    val system: ActorSystem = ActorSystem("java-serializer-roundtrip", config)

    try {
      val serialization: Serialization = SerializationExtension(system)
      val payload: JavaSerializerPayload = new JavaSerializerPayload("message-id", 42)
      val serializer: Serializer = serialization.findSerializerFor(payload)

      assertThat(serializer).isInstanceOf(classOf[JavaSerializer])

      val bytes: Array[Byte] = serialization.serialize(payload).get
      val deserialized: AnyRef = serialization.deserialize(bytes, serializer.identifier, "").get

      assertThat(deserialized).isEqualTo(payload)
    } finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}

@SerialVersionUID(1L)
final class JavaSerializerPayload(val id: String, val count: Int) extends Serializable {
  override def equals(other: Any): Boolean = other match {
    case that: JavaSerializerPayload => id == that.id && count == that.count
    case _ => false
  }

  override def hashCode(): Int = 31 * id.hashCode + count

  override def toString: String = s"JavaSerializerPayload($id,$count)"
}
