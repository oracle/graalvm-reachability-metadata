/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import org.apache.pekko.actor.AbstractActor
import org.apache.pekko.actor.Props
import org.apache.pekko.japi.Creator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AbstractPropsTest {
  @Test
  def acceptsCreatorClassWithVisibleConstructor(): Unit = {
    val props: Props = Props.create(
      classOf[AbstractPropsTest.SilentActor],
      new AbstractPropsTest.VisibleCreator)

    assertThat(props.actorClass()).isEqualTo(classOf[AbstractPropsTest.SilentActor])
  }

  @Test
  def acceptsCreatorClassWithNoPublicConstructors(): Unit = {
    val props: Props = AbstractPropsPrivateCreatorProbe.createPropsWithPackagePrivateCreator()

    assertThat(props.actorClass()).isEqualTo(AbstractPropsPrivateCreatorProbe.actorClass())
  }
}

object AbstractPropsTest {
  final class SilentActor extends AbstractActor {
    override def createReceive(): AbstractActor.Receive = AbstractActor.emptyBehavior
  }

  final class VisibleCreator extends Creator[SilentActor] {
    override def create(): SilentActor = new SilentActor
  }

}
