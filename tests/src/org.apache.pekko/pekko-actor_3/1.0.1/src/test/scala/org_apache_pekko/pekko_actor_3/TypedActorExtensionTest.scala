/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.TypedActor
import org.apache.pekko.actor.TypedActorExtension
import org.apache.pekko.actor.TypedProps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TypedActorExtensionTest {
  @Test
  def createsProxyBackedByProvidedActorRef(): Unit = {
    val system: ActorSystem = ActorSystem("typed-actor-extension-proxy")
    try {
      val extension: TypedActorExtension = TypedActor(system)
      val props: TypedProps[TypedActorExtensionProxyImpl] =
        TypedProps(classOf[TypedActorExtensionProxyApi], new TypedActorExtensionProxyImpl)

      val proxy: TypedActorExtensionProxyApi =
        extension.typedActorOf[TypedActorExtensionProxyApi, TypedActorExtensionProxyImpl](props, system.deadLetters)

      assertTrue(extension.isTypedActor(proxy.asInstanceOf[AnyRef]))
      assertEquals(system.deadLetters, extension.getActorRefFor(proxy.asInstanceOf[AnyRef]))
      assertFalse(extension.isTypedActor(new Object))
    } finally Await.result(system.terminate(), 10.seconds)
  }
}

trait TypedActorExtensionProxyApi {
  def reply(): String
}

final class TypedActorExtensionProxyImpl extends TypedActorExtensionProxyApi {
  override def reply(): String = "reply"
}
