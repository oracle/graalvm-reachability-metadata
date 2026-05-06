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
  def acceptsCreatorThatDoesNotCloseOverActorState(): Unit = {
    val creator: Creator[AbstractPropsActor] = ClosingOverCreator()

    val props: Props = Props.create[AbstractPropsActor](classOf[AbstractPropsActor], creator)

    assertThat(props.actorClass()).isEqualTo(classOf[AbstractPropsActor])
  }

  private object ClosingOverCreator {
    def apply(): Creator[AbstractPropsActor] = new ClosingOverCreator
  }

  private final class ClosingOverCreator private () extends Creator[AbstractPropsActor] {
    override def create(): AbstractPropsActor = new AbstractPropsActor
  }
}

class AbstractPropsActor extends Actor {
  override def receive: Receive = Actor.emptyBehavior
}
