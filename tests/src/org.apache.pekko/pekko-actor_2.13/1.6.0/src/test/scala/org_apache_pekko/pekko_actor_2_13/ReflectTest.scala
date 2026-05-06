/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package sun.reflect {
  object Reflection {
    def getCallerClass(depth: Int): Class[_] =
      ClassLoader.getSystemClassLoader.loadClass("org_apache_pekko.pekko_actor_2_13.ReflectCallerClassProbe")
  }
}

package org_apache_pekko.pekko_actor_2_13 {
  import java.util.concurrent.CountDownLatch
  import java.util.concurrent.TimeUnit
  import java.util.concurrent.atomic.AtomicReference

  import scala.concurrent.Await
  import scala.concurrent.duration.DurationInt

  import org.apache.pekko.actor.AbstractActor
  import org.apache.pekko.actor.ActorSystem
  import org.apache.pekko.actor.ExtendedActorSystem
  import org.apache.pekko.actor.Props
  import org.assertj.core.api.Assertions.assertThat
  import org.junit.jupiter.api.Test

  class ReflectTest {
    @Test
    def createsPrivateNoArgumentActorWhileCallerClassFallbackIsActive(): Unit = {
      val originalClassLoader: ClassLoader = Thread.currentThread().getContextClassLoader
      Thread.currentThread().setContextClassLoader(null)

      val constructed: CountDownLatch = new CountDownLatch(1)
      ReflectPrivateConstructorActor.constructed.set(constructed)

      var system: ActorSystem = null
      try {
        system = ActorSystem(s"reflect-test-${System.nanoTime()}")
        system.actorOf(Props.create(classOf[ReflectPrivateConstructorActor]))

        assertThat(constructed.await(10, TimeUnit.SECONDS)).isTrue
        assertThat(system.asInstanceOf[ExtendedActorSystem].dynamicAccess.classLoader).isNotNull
      } finally {
        if (system != null) Await.result(system.terminate(), 10.seconds)
        Thread.currentThread().setContextClassLoader(originalClassLoader)
      }
    }
  }

  final class ReflectCallerClassProbe

  final class ReflectPrivateConstructorActor private () extends AbstractActor {
    ReflectPrivateConstructorActor.constructed.get().countDown()

    override def createReceive(): AbstractActor.Receive = AbstractActor.emptyBehavior
  }

  object ReflectPrivateConstructorActor {
    val constructed: AtomicReference[CountDownLatch] = new AtomicReference[CountDownLatch](new CountDownLatch(1))
  }
}
