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
import org.apache.pekko.actor.TypedActor
import org.apache.pekko.serialization.JavaSerializer

import java.lang.reflect.Method
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class TypedActorInnerSerializedMethodCallTest {
  @Test
  def deserializesMethodCallByResolvingDeclaredMethod(): Unit = {
    val config: Config = ConfigFactory.parseString("pekko.actor.allow-java-serialization = on")
    val system: ActorSystem = ActorSystem(s"typed-actor-serialized-method-call-${System.nanoTime()}", config)
    try {
      val serializer: JavaSerializer = new JavaSerializer(system.asInstanceOf[ExtendedActorSystem])
      val method: Method = classOf[TypedActorInnerSerializedMethodCallTarget].getMethod("value")
      val methodCall: TypedActor.MethodCall = TypedActor.MethodCall(method, null)

      val bytes: Array[Byte] = serializer.toBinary(methodCall)
      val deserialized: AnyRef = serializer.fromBinary(bytes, None)
      val restoredMethodCall: TypedActor.MethodCall = deserialized.asInstanceOf[TypedActor.MethodCall]
      val target: TypedActorInnerSerializedMethodCallTarget = new TypedActorInnerSerializedMethodCallTarget

      val result: AnyRef = restoredMethodCall(target)

      assertThat(bytes.length).isGreaterThan(0)
      assertThat(result).isEqualTo("resolved")
      assertThat(target.invocations).isEqualTo(1)
    } finally {
      Await.result(system.terminate(), 5.seconds)
    }
  }
}

class TypedActorInnerSerializedMethodCallTarget {
  var invocations: Int = 0

  def value(): String = {
    invocations += 1
    "resolved"
  }
}
