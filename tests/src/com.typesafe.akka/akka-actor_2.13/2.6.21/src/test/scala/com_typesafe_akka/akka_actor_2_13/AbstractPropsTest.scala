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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AbstractPropsTest {
  @Test
  def acceptsEnclosedCreatorWithPublicConstructor(): Unit = {
    val creator: Creator[AbstractPropsConstructorProbeActor] = new PublicConstructorCreator
    val props: Props = Props.create(classOf[AbstractPropsConstructorProbeActor], creator)

    assertThat(props.actorClass()).isEqualTo(classOf[AbstractPropsConstructorProbeActor])
  }

  @Test
  def rejectsJavaLocalCreatorWithoutPublicConstructor(): Unit = {
    val creator: Creator[AbstractPropsConstructorProbeActor] = new AbstractPropsJavaCreatorFactory()
      .newNonStaticLocalCreator()
      .asInstanceOf[Creator[AbstractPropsConstructorProbeActor]]

    assertThatThrownBy(() => Props.create(classOf[AbstractPropsConstructorProbeActor], creator))
      .isInstanceOf(classOf[IllegalArgumentException])
      .hasMessageContaining("cannot use non-static local Creator")
  }

  final class PublicConstructorCreator extends Creator[AbstractPropsConstructorProbeActor] {
    override def create(): AbstractPropsConstructorProbeActor = new AbstractPropsConstructorProbeActor
  }
}

final class AbstractPropsConstructorProbeActor extends Actor {
  override def receive: Receive = Actor.emptyBehavior
}
