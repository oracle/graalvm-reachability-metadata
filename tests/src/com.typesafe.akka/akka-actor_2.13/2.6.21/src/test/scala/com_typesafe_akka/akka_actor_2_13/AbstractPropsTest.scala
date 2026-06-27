/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import akka.actor.Actor
import akka.actor.Props
import akka.japi.Creator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AbstractPropsTest {
  @Test
  def acceptsEnclosedCreatorWithPublicConstructor(): Unit = {
    val creator: Creator[AbstractPropsConstructorProbeActor] = new PublicConstructorCreator
    val props: Props = Props.create(classOf[AbstractPropsConstructorProbeActor], creator)

    assertThat(props.actorClass()).isEqualTo(classOf[AbstractPropsConstructorProbeActor])
  }

  @Test
  def acceptsEnclosedCreatorSingletonWithPrivateConstructor(): Unit = {
    val creator: Creator[AbstractPropsConstructorProbeActor] = AbstractPropsCreatorHolder.PrivateConstructorCreator
    val props: Props = Props.create(classOf[AbstractPropsConstructorProbeActor], creator)

    assertThat(props.actorClass()).isEqualTo(classOf[AbstractPropsConstructorProbeActor])
  }

  final class PublicConstructorCreator extends Creator[AbstractPropsConstructorProbeActor] {
    override def create(): AbstractPropsConstructorProbeActor = new AbstractPropsConstructorProbeActor
  }
}

object AbstractPropsCreatorHolder {
  object PrivateConstructorCreator extends Creator[AbstractPropsConstructorProbeActor] {
    override def create(): AbstractPropsConstructorProbeActor = new AbstractPropsConstructorProbeActor
  }
}

final class AbstractPropsConstructorProbeActor extends Actor {
  override def receive: Receive = Actor.emptyBehavior
}
