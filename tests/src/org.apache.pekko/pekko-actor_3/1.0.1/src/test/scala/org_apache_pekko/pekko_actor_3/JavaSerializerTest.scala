/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.serialization.JavaSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class JavaSerializerTest {
  @Test
  def serializesAndDeserializesUsingJavaObjectStreams(): Unit = {
    val config: Config = ConfigFactory.parseString("pekko.actor.allow-java-serialization = on")
    val system: ActorSystem = ActorSystem(s"java-serializer-${System.nanoTime()}", config)
    try {
      val serializer: JavaSerializer = new JavaSerializer(system.asInstanceOf[ExtendedActorSystem])
      val message: String = "message serialized through org.apache.pekko.serialization.JavaSerializer"

      val bytes: Array[Byte] = serializer.toBinary(message)
      val deserialized: AnyRef = serializer.fromBinary(bytes, None)

      assertThat(bytes.length).isGreaterThan(0)
      assertThat(deserialized).isEqualTo(message)
    } finally {
      Await.result(system.terminate(), 5.seconds)
    }
  }
}
