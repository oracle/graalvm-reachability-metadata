/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import java.lang.reflect.Method

import akka.actor.ActorSystem
import akka.actor.TypedActor.MethodCall
import akka.serialization.SerializationExtension
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class TypedActorInnerSerializedMethodCallTest {

  @Test
  def javaSerializerRoundTripsMethodCallWithEmptyParameters(): Unit = {
    withActorSystem("typed-actor-serialized-method-call") { system: ActorSystem =>
      val method: Method = classOf[TypedActorSerializedMethodCallTarget].getMethod("answer")
      val call: MethodCall = MethodCall(method, Array.empty[AnyRef])
      val serialization = SerializationExtension(system)

      val bytes: Array[Byte] = serialization.serialize(call).get
      val restored: MethodCall = serialization
        .deserialize(bytes, serialization.findSerializerFor(call).identifier, "")
        .get
        .asInstanceOf[MethodCall]

      val target: TypedActorSerializedMethodCallTarget = new TypedActorSerializedMethodCallTargetImpl
      assertThat(restored(target)).isEqualTo("serialized-answer")
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

trait TypedActorSerializedMethodCallTarget {
  def answer(): String
}

final class TypedActorSerializedMethodCallTargetImpl extends TypedActorSerializedMethodCallTarget {
  override def answer(): String = "serialized-answer"
}
