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
import org.apache.pekko.actor.TypedActor.MethodCall
import org.apache.pekko.serialization.JavaSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypedActorInnerSerializedMethodCallTest {
  @Test
  def deserializesMethodCallAndResolvesDeclaredMethod(): Unit = {
    val config: Config = ConfigFactory.parseString("pekko.actor.allow-java-serialization = on")
    val system: ActorSystem = ActorSystem("typed-actor-serialized-method-call", config)
    try {
      val serializer: JavaSerializer = new JavaSerializer(system.asInstanceOf[ExtendedActorSystem])
      val call: MethodCall = MethodCall(classOf[TypedActorSerializedMethodCallTarget].getMethod("ping"), null)

      val restored: MethodCall = serializer.fromBinary(serializer.toBinary(call), None).asInstanceOf[MethodCall]

      assertEquals("pong", restored(new TypedActorSerializedMethodCallTarget))
    } finally Await.result(system.terminate(), 10.seconds)
  }
}

final class TypedActorSerializedMethodCallTarget {
  def ping(): String = "pong"
}
