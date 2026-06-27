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
import org.apache.pekko.actor.TypedActor
import org.apache.pekko.serialization.Serialization
import org.apache.pekko.serialization.SerializationExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

@nowarn("msg=deprecated")
class TypedActorInnerSerializedMethodCallTest {
  @Test
  def deserializesMethodCallAndInvokesResolvedMethod(): Unit = {
    val config: Config = ConfigFactory.parseString("""
        pekko.actor.allow-java-serialization = on
        pekko.actor.warn-about-java-serializer-usage = off
        """)
    val system: ActorSystem = ActorSystem("typed-actor-serialized-method-call", config)

    try {
      val serialization: Serialization = SerializationExtension(system)
      val method: java.lang.reflect.Method = classOf[SerializedMethodCallRoundTripTarget].getMethod("greeting")
      val call: TypedActor.MethodCall = TypedActor.MethodCall(method, Array.empty[AnyRef])
      val serializerId: Int = serialization.findSerializerFor(call).identifier

      val bytes: Array[Byte] = serialization.serialize(call).get
      val deserialized: AnyRef = serialization.deserialize(bytes, serializerId, "").get

      assertThat(deserialized).isInstanceOf(classOf[TypedActor.MethodCall])
      val roundTrippedCall: TypedActor.MethodCall = deserialized.asInstanceOf[TypedActor.MethodCall]
      assertThat(roundTrippedCall(new SerializedMethodCallRoundTripTarget)).isEqualTo("hello")
    } finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}

final class SerializedMethodCallRoundTripTarget {
  def greeting(): String = "hello"
}
