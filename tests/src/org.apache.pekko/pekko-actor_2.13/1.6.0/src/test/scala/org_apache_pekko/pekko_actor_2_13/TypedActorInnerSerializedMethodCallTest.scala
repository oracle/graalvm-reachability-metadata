/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.actor.TypedActor
import org.apache.pekko.serialization.JavaSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypedActorInnerSerializedMethodCallTest {
  @Test
  def roundTripsMethodCallThroughSerializedMethodCall(): Unit = {
    val system: ActorSystem = ActorSystem("typed-actor-serialized-method-call-test", javaSerializationConfig())
    try {
      val serializer: JavaSerializer = new JavaSerializer(system.asInstanceOf[ExtendedActorSystem])
      val original: TypedActor.MethodCall = TypedActor.MethodCall(
        classOf[TypedActorInnerSerializedMethodCallTarget].getMethod("noParameters"),
        Array.empty[AnyRef])

      val bytes: Array[Byte] = serializer.toBinary(original)
      val restored: TypedActor.MethodCall = serializer.fromBinary(bytes, None).asInstanceOf[TypedActor.MethodCall]
      val result: AnyRef = restored(new TypedActorInnerSerializedMethodCallTarget)

      assertThat(result).isEqualTo("serialized-method-call")
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

final class TypedActorInnerSerializedMethodCallTarget {
  def noParameters(): String = "serialized-method-call"
}
