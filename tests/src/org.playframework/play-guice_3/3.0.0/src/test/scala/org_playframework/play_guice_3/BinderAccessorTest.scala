/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_guice_3

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Key
import org.apache.pekko.actor.AbstractActor
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import play.libs.pekko.PekkoGuiceSupport

class BinderAccessorTest {
  @Test
  def bindsActorFactoryThroughPekkoGuiceSupport(): Unit = {
    val injector = Guice.createInjector(new BinderAccessorModule)
    val factoryBinding = injector.getExistingBinding(Key.get(classOf[BinderAccessorTestActorFactory]))

    assertThat(factoryBinding).isNotNull
  }
}

class BinderAccessorModule extends AbstractModule with PekkoGuiceSupport {
  override protected def configure(): Unit = {
    bindActorFactory(classOf[BinderAccessorTestActor], classOf[BinderAccessorTestActorFactory])
  }
}

class BinderAccessorTestActor extends AbstractActor {
  override def createReceive(): AbstractActor.Receive = {
    receiveBuilder().matchAny(_ => ()).build()
  }
}

trait BinderAccessorTestActorFactory {
  def create(): BinderAccessorTestActor
}
