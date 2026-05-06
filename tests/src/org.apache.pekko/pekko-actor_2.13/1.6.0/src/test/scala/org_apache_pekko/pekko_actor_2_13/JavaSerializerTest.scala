/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import java.io.Serializable

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.serialization.JavaSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JavaSerializerTest {
  @Test
  def roundTripsSerializableObjectThroughJavaSerializer(): Unit = {
    val system: ActorSystem = ActorSystem("java-serializer-test", javaSerializationConfig())
    try {
      val serializer: JavaSerializer = new JavaSerializer(system.asInstanceOf[ExtendedActorSystem])
      val original: JavaSerializerTest.SerializableMessage =
        JavaSerializerTest.SerializableMessage("pekko-java-serializer", 42)

      val bytes: Array[Byte] = serializer.toBinary(original)
      val restored: AnyRef = serializer.fromBinary(bytes, None)

      assertThat(restored).isEqualTo(original)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  private def javaSerializationConfig(): Config = {
    ConfigFactory.parseString("""
      pekko.actor.allow-java-serialization = on
      pekko.actor.warn-about-java-serializer-usage = off
      pekko.loggers = ["org.apache.pekko.event.Logging$DefaultLogger"]
      """)
  }
}

object JavaSerializerTest {
  @SerialVersionUID(1L)
  final case class SerializableMessage(name: String, value: Int) extends Serializable
}
