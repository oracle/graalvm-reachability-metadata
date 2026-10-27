/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorRef
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

class TypedActorExtensionTest {
  @Test
  @nowarn("msg=deprecated")
  def createsProxyForTypedActorAndTracksItsActorRef(): Unit = {
    val config: Config = ConfigFactory.parseString("pekko.actor.typed.timeout = 3s")
    val system: ActorSystem = ActorSystem(s"typed-actor-extension-${System.nanoTime()}", config)
    try {
      val typedActors: TypedActorExtension = TypedActor(system)
      val props: TypedProps[TypedActorExtensionGreeting] = TypedProps(
        classOf[TypedActorExtensionGreeting],
        new TypedActorExtensionGreetingImpl).withTimeout(Timeout(3.seconds))

      val proxy: TypedActorExtensionGreeting = typedActors.typedActorOf(props)
      val actorRef: ActorRef = typedActors.getActorRefFor(proxy)

      assertThat(typedActors.isTypedActor(proxy)).isTrue()
      assertThat(actorRef).isNotNull()
      assertThat(proxy.greeting("Pekko")).isEqualTo("hello, Pekko")
      assertThat(proxy.selfIsTypedActor()).isTrue()
    } finally {
      Await.result(system.terminate(), 5.seconds)
    }
  }
}

trait TypedActorExtensionGreeting {
  def greeting(name: String): String

  def selfIsTypedActor(): java.lang.Boolean
}

class TypedActorExtensionGreetingImpl extends TypedActorExtensionGreeting {
  override def greeting(name: String): String = s"hello, $name"

  @nowarn("msg=deprecated")
  override def selfIsTypedActor(): java.lang.Boolean = {
    val self: TypedActorExtensionGreeting = TypedActor.self[TypedActorExtensionGreeting]
    java.lang.Boolean.valueOf(self ne null)
  }
}
