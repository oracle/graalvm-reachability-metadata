/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.serialization.JavaSerializer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JavaSerializerTest {
  @Test
  def serializesAndDeserializesSerializablePayload(): Unit = {
    val config: Config = ConfigFactory.parseString("pekko.actor.allow-java-serialization = on")
    val system: ActorSystem = ActorSystem("java-serializer-test", config)
    try {
      val serializer: JavaSerializer = new JavaSerializer(system.asInstanceOf[ExtendedActorSystem])
      val payload: JavaSerializerPayload = JavaSerializerPayload("pekko", 101, Array[Byte](1, 2, 3, 5, 8))

      val bytes: Array[Byte] = serializer.toBinary(payload)
      val deserialized: AnyRef = serializer.fromBinary(bytes, None)

      val restored: JavaSerializerPayload = deserialized.asInstanceOf[JavaSerializerPayload]
      assertEquals(payload.name, restored.name)
      assertEquals(payload.value, restored.value)
      assertArrayEquals(payload.bytes, restored.bytes)
    } finally Await.result(system.terminate(), 10.seconds)
  }
}

final case class JavaSerializerPayload(name: String, value: Int, bytes: Array[Byte]) extends Serializable
