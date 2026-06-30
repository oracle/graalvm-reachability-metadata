/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.Props
import org.apache.pekko.japi.Creator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AbstractPropsTest {
  @Test
  def acceptsEnclosedCreatorWithPublicConstructor(): Unit = {
    val creator: Creator[ConstructorProbeActor] = new PublicConstructorCreator
    val props: Props = Props.create(classOf[ConstructorProbeActor], creator)

    assertThat(props.actorClass()).isEqualTo(classOf[ConstructorProbeActor])
  }

  final class PublicConstructorCreator extends Creator[ConstructorProbeActor] {
    override def create(): ConstructorProbeActor = new ConstructorProbeActor
  }
}

final class ConstructorProbeActor extends Actor {
  override def receive: Receive = Actor.emptyBehavior
}
