/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.TypedActor
import akka.actor.TypedActorExtension
import akka.actor.TypedProps
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@nowarn("msg=deprecated")
class TypedActorExtensionTest {
  @Test
  def createsProxyForTypedActor(): Unit = {
    val system: ActorSystem = ActorSystem("typed-actor-extension-test")

    try {
      val typedActor: TypedActorExtension = TypedActor(system)
      val proxy: GreetingService = typedActor.typedActorOf(TypedProps(classOf[GreetingService], new GreetingServiceImpl))
      val actorRef: ActorRef = typedActor.getActorRefFor(proxy)

      assertThat(typedActor.isTypedActor(proxy)).isTrue()
      assertThat(actorRef).isNotNull()
      assertThat(proxy.greet("Akka")).isEqualTo("hello Akka")
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}

trait GreetingService {
  def greet(name: String): String
}

class GreetingServiceImpl extends GreetingService {
  override def greet(name: String): String = s"hello $name"
}
