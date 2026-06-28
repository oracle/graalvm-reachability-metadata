/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.Props
import org.apache.pekko.japi.Creator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AbstractPropsTest {
  @Test
  def createWithPublicMemberCreatorValidatesAvailablePublicConstructors(): Unit = {
    val props: Props = Props.create(classOf[AbstractPropsActor], new PublicMemberCreator())

    assertThat(props.actorClass()).isEqualTo(classOf[AbstractPropsActor])
  }

  @Test
  def createWithStaticAnonymousCreatorValidatesDeclaredConstructors(): Unit = {
    val actorClass: Class[AbstractPropsJavaHelper.JavaActor] = classOf[AbstractPropsJavaHelper.JavaActor]
    val props: Props = Props.create(actorClass, AbstractPropsJavaHelper.staticAnonymousCreator())

    assertThat(props.actorClass()).isEqualTo(actorClass)
  }

  class PublicMemberCreator extends Creator[AbstractPropsActor] {
    override def create(): AbstractPropsActor = new AbstractPropsActor
  }
}

class AbstractPropsActor extends Actor {
  override def receive: Receive = Actor.emptyBehavior
}
