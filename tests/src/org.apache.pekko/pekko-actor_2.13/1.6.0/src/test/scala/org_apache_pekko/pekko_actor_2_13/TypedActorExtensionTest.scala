/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.TypedActor
import org.apache.pekko.actor.TypedActorExtension
import org.apache.pekko.actor.TypedProps
import org.apache.pekko.util.Timeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class TypedActorExtensionTest {
  @Test
  def createsProxyForTypedActorInterface(): Unit = {
    val system: ActorSystem = ActorSystem(s"typed-actor-extension-${System.nanoTime()}")

    try {
      val props: TypedProps[TypedActorExtensionGreeter] =
        TypedProps[TypedActorExtensionGreeter](
          classOf[TypedActorExtensionGreeter],
          new TypedActorExtensionGreeterImpl: TypedActorExtensionGreeter)
          .withTimeout(Timeout(3.seconds))
      val extension: TypedActorExtension = TypedActor(system)
      val proxy: TypedActorExtensionGreeter =
        extension.typedActorOf[TypedActorExtensionGreeter, TypedActorExtensionGreeter](props)

      try {
        assertThat(extension.isTypedActor(proxy.asInstanceOf[AnyRef])).isTrue
        assertThat(extension.getActorRefFor(proxy.asInstanceOf[AnyRef])).isNotNull
        assertThat(proxy.greet("Pekko")).isEqualTo("hello Pekko")
      } finally {
        extension.stop(proxy.asInstanceOf[AnyRef])
      }
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}

trait TypedActorExtensionGreeter {
  def greet(name: String): String
}

final class TypedActorExtensionGreeterImpl extends TypedActorExtensionGreeter {
  override def greet(name: String): String = s"hello $name"
}
