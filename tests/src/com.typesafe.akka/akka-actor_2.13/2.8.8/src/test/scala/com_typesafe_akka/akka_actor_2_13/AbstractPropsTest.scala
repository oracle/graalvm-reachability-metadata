/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.japi.Creator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class AbstractPropsTest {

  @Test
  def propsCreateAcceptsJavaAnonymousCreatorWithNonPublicDeclaredConstructor(): Unit = {
    val creator: Creator[AbstractPropsCreatorActor] = AbstractPropsCreatorFactory.anonymousCreator()
    val props: Props = Props.create[AbstractPropsCreatorActor](
      classOf[AbstractPropsCreatorActor],
      creator)

    assertThat(props.actorClass()).isEqualTo(classOf[AbstractPropsCreatorActor])

    withActorSystem("abstract-props-anonymous-creator") { system: ActorSystem =>
      val recorder: AtomicReference[String] = new AtomicReference[String]()
      val latch: CountDownLatch = new CountDownLatch(1)
      val actor: ActorRef = system.actorOf(props, "anonymous-creator")

      actor.tell(new AbstractPropsCreatorActor.Message(recorder, latch), ActorRef.noSender)

      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
      assertThat(recorder.get()).isEqualTo("anonymous-created")
    }
  }

  private def withActorSystem(name: String)(body: ActorSystem => Unit): Unit = {
    val system: ActorSystem = ActorSystem(name)
    try body(system)
    finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}
