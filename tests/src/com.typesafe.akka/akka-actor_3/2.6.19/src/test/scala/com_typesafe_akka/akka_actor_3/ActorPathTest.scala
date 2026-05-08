/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_3

import akka.actor.ActorPath
import akka.actor.Address
import akka.actor.InvalidActorNameException
import akka.actor.RootActorPath
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

import java.util.Arrays

class ActorPathTest {
  @Test
  def addressesRepresentLocalAndGloballyScopedActorSystems(): Unit = {
    val local: Address = Address("akka", "inventory")
    assertThat(local.hasLocalScope).isTrue()
    assertThat(local.hasGlobalScope).isFalse()
    assertThat(local.getHost()).isEmpty()
    assertThat(local.getPort()).isEmpty()
    assertThat(local.toString).isEqualTo("akka://inventory")

    val remote: Address = Address("akka", "inventory", "127.0.0.1", 25520)
    assertThat(remote.hasLocalScope).isFalse()
    assertThat(remote.hasGlobalScope).isTrue()
    assertThat(remote.getHost()).hasValue("127.0.0.1")
    assertThat(remote.getPort()).hasValue(25520)
    assertThat(remote.hostPort).isEqualTo("inventory@127.0.0.1:25520")
    assertThat(remote.toString).isEqualTo("akka://inventory@127.0.0.1:25520")
  }

  @Test
  def actorPathsCanBeBuiltParsedAndRenderedForSerialization(): Unit = {
    val address: Address = Address("akka", "inventory", "127.0.0.1", 25520)
    val path: ActorPath = RootActorPath(address).descendant(Arrays.asList("user", "orders", "worker-1"))

    assertThat(path.name).isEqualTo("worker-1")
    assertThat(path.parent.name).isEqualTo("orders")
    assertThat(path.root.address).isEqualTo(address)
    assertThat(path.elements.toList).isEqualTo(List("user", "orders", "worker-1"))
    assertThat(path.toStringWithoutAddress).isEqualTo("/user/orders/worker-1")
    assertThat(path.toSerializationFormat).isEqualTo("akka://inventory@127.0.0.1:25520/user/orders/worker-1")

    val parsed: ActorPath = ActorPath.fromString(path.toSerializationFormat)
    assertThat(parsed).isEqualTo(path)
    assertThat(parsed.compareTo(path)).isZero()
  }

  @Test
  def localActorPathsCanBeRenderedWithAnExplicitAddress(): Unit = {
    val localRoot: ActorPath = RootActorPath(Address("akka", "inventory"))
    val localPath: ActorPath = localRoot / "user" / "projection" / "worker"
    val externalAddress: Address = Address("akka", "inventory", "localhost", 25521)

    assertThat(localPath.address.hasLocalScope).isTrue()
    assertThat(localPath.toStringWithoutAddress).isEqualTo("/user/projection/worker")
    assertThat(localPath.toStringWithAddress(externalAddress))
      .isEqualTo("akka://inventory@localhost:25521/user/projection/worker")
    assertThat(localPath.toSerializationFormatWithAddress(externalAddress))
      .isEqualTo("akka://inventory@localhost:25521/user/projection/worker")
  }

  @Test
  def actorPathElementValidationAcceptsSafeNamesAndRejectsMalformedNames(): Unit = {
    assertThat(ActorPath.isValidPathElement("worker-1_$")).isTrue()
    assertThat(ActorPath.isValidPathElement("")).isFalse()
    assertThat(ActorPath.isValidPathElement("bad/name")).isFalse()
    assertThat(ActorPath.isValidPathElement("bad#uid")).isFalse()

    assertThatThrownBy(() => ActorPath.validatePathElement("bad/name"))
      .isInstanceOf(classOf[InvalidActorNameException])
  }
}
