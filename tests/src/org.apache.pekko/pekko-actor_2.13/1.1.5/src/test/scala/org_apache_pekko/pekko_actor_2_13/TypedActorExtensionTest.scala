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

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

@nowarn("msg=deprecated")
class TypedActorExtensionTest {
  @Test
  def createsProxyForTypedActorInterfaceAndDispatchesMethodCalls(): Unit = {
    val system: ActorSystem = ActorSystem("typed-actor-extension-proxy")
    val extension: TypedActorExtension = TypedActor(system)
    var proxy: TypedActorExtensionGreeting = null

    try {
      val props: TypedProps[TypedActorExtensionGreetingImpl] =
        TypedProps(classOf[TypedActorExtensionGreeting], new TypedActorExtensionGreetingImpl)
          .withTimeout(Timeout(10.seconds))

      proxy = extension.typedActorOf[TypedActorExtensionGreeting, TypedActorExtensionGreetingImpl](props)

      assertThat(extension.isTypedActor(proxy.asInstanceOf[AnyRef])).isTrue
      assertThat(extension.getActorRefFor(proxy.asInstanceOf[AnyRef])).isNotNull
      assertThat(proxy.greeting("Pekko")).isEqualTo("hello Pekko")
    } finally {
      if (proxy != null) {
        extension.stop(proxy.asInstanceOf[AnyRef])
      }
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}

trait TypedActorExtensionGreeting {
  def greeting(name: String): String
}

final class TypedActorExtensionGreetingImpl extends TypedActorExtensionGreeting {
  override def greeting(name: String): String = s"hello $name"
}
