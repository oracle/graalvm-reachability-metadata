/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.vault_3

import cats.Contravariant
import cats.Functor
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.typelevel.vault.InsertKey
import org.typelevel.vault.Key
import org.typelevel.vault.Locker
import org.typelevel.vault.LookupKey
import org.typelevel.vault.Vault

class Vault_3Test {
  @Test
  def storesTypedValuesWithoutMutatingPreviousVaults(): Unit = {
    val stringKey: Key[String] = newKey[String]()
    val intKey: Key[Int] = newKey[Int]()
    val missingKey: Key[Double] = newKey[Double]()

    val empty: Vault = Vault.empty

    assertThat(empty.isEmpty).isTrue
    assertThat(empty.size).isEqualTo(0)
    assertThat(empty.contains(stringKey)).isFalse
    assertThat(empty.lookup(stringKey)).isEqualTo(None)

    val withString: Vault = empty.insert(stringKey, "alpha")

    assertThat(empty.isEmpty).isTrue
    assertThat(empty.lookup(stringKey)).isEqualTo(None)
    assertThat(withString.isEmpty).isFalse
    assertThat(withString.size).isEqualTo(1)
    assertThat(withString.contains(stringKey)).isTrue
    assertThat(withString.contains(intKey)).isFalse
    assertThat(withString.lookup(stringKey)).isEqualTo(Some("alpha"))
    assertThat(withString.lookup(intKey)).isEqualTo(None)

    val withBoth: Vault = withString.insert(intKey, 7)
    val overwritten: Vault = withBoth.insert(stringKey, "beta")
    val adjusted: Vault = overwritten.adjust(intKey, value => value + 5)
    val unchangedByMissingAdjustment: Vault = adjusted.adjust(missingKey, value => value + 1.0d)
    val withoutString: Vault = unchangedByMissingAdjustment.delete(stringKey)
    val afterDeletingMissingAgain: Vault = withoutString.delete(stringKey)

    assertThat(withBoth.size).isEqualTo(2)
    assertThat(withBoth.lookup(stringKey)).isEqualTo(Some("alpha"))
    assertThat(withBoth.lookup(intKey)).isEqualTo(Some(7))
    assertThat(overwritten.size).isEqualTo(2)
    assertThat(overwritten.lookup(stringKey)).isEqualTo(Some("beta"))
    assertThat(overwritten.lookup(intKey)).isEqualTo(Some(7))
    assertThat(adjusted.lookup(intKey)).isEqualTo(Some(12))
    assertThat(unchangedByMissingAdjustment.size).isEqualTo(2)
    assertThat(unchangedByMissingAdjustment.lookup(missingKey)).isEqualTo(None)
    assertThat(withoutString.size).isEqualTo(1)
    assertThat(withoutString.contains(stringKey)).isFalse
    assertThat(withoutString.lookup(intKey)).isEqualTo(Some(12))
    assertThat(afterDeletingMissingAgain.size).isEqualTo(1)
  }

  @Test
  def unionCombinesVaultsAndRightHandValuesWinConflicts(): Unit = {
    val sharedKey: Key[String] = newKey[String]()
    val leftOnlyKey: Key[Int] = newKey[Int]()
    val rightOnlyKey: Key[Boolean] = newKey[Boolean]()

    val left: Vault = Vault.empty
      .insert(sharedKey, "left")
      .insert(leftOnlyKey, 10)
    val right: Vault = Vault.empty
      .insert(sharedKey, "right")
      .insert(rightOnlyKey, true)

    val mergedWithRightPriority: Vault = left ++ right
    val mergedWithLeftPriority: Vault = right ++ left

    assertThat(mergedWithRightPriority.size).isEqualTo(3)
    assertThat(mergedWithRightPriority.lookup(sharedKey)).isEqualTo(Some("right"))
    assertThat(mergedWithRightPriority.lookup(leftOnlyKey)).isEqualTo(Some(10))
    assertThat(mergedWithRightPriority.lookup(rightOnlyKey)).isEqualTo(Some(true))
    assertThat(mergedWithLeftPriority.lookup(sharedKey)).isEqualTo(Some("left"))
    assertThat(mergedWithLeftPriority.lookup(leftOnlyKey)).isEqualTo(Some(10))
    assertThat(mergedWithLeftPriority.lookup(rightOnlyKey)).isEqualTo(Some(true))
    assertThat(left.lookup(sharedKey)).isEqualTo(Some("left"))
    assertThat(right.lookup(sharedKey)).isEqualTo(Some("right"))
  }

  @Test
  def mappedInsertAndLookupKeysTransformValuesAroundSharedKeyIdentity(): Unit = {
    val rawKey: Key[Int] = newKey[Int]()
    val otherKey: Key[Int] = newKey[Int]()
    val numericStringInsertKey: InsertKey[String] = rawKey.contramap[String](_.toInt)
    val labeledLookupKey: LookupKey[String] = rawKey.map(value => s"count=$value")

    val vault: Vault = Vault.empty.insert(numericStringInsertKey, "42")
    val lockerFromInsertKey: Locker = Locker(numericStringInsertKey, "64")
    val lockerFromRawKey: Locker = Locker(rawKey, 5)
    val deleted: Vault = vault.delete(labeledLookupKey)

    assertThat(vault.contains(rawKey)).isTrue
    assertThat(vault.contains(labeledLookupKey)).isTrue
    assertThat(vault.lookup(rawKey)).isEqualTo(Some(42))
    assertThat(vault.lookup(labeledLookupKey)).isEqualTo(Some("count=42"))
    assertThat(deleted.contains(rawKey)).isFalse
    assertThat(deleted.lookup(rawKey)).isEqualTo(None)
    assertThat(lockerFromInsertKey.unlock(rawKey)).isEqualTo(Some(64))
    assertThat(lockerFromInsertKey.unlock(labeledLookupKey)).isEqualTo(Some("count=64"))
    assertThat(lockerFromInsertKey.unlock(otherKey)).isEqualTo(None)
    assertThat(lockerFromRawKey.unlock(rawKey)).isEqualTo(Some(5))
    assertThat(lockerFromRawKey.unlock(labeledLookupKey)).isEqualTo(Some("count=5"))
  }

  @Test
  def invariantMappingsRoundTripThroughKeys(): Unit = {
    val intKey: Key[Int] = newKey[Int]()
    val booleanKey: Key[Boolean] = intKey.imap[Boolean]((value: Int) => value > 0) { (value: Boolean) =>
      if (value) 1 else 0
    }
    val textKey: Key[String] = intKey.imap[String]((value: Int) => value.toString)((value: String) => value.toInt)

    val booleanVault: Vault = Vault.empty.insert(booleanKey, true)
    val intVault: Vault = Vault.empty.insert(intKey, 0)
    val textVault: Vault = Vault.empty.insert(textKey, "123")

    assertThat(booleanVault.lookup(booleanKey)).isEqualTo(Some(true))
    assertThat(booleanVault.lookup(intKey)).isEqualTo(Some(1))
    assertThat(booleanVault.lookup(textKey)).isEqualTo(Some("1"))
    assertThat(intVault.lookup(intKey)).isEqualTo(Some(0))
    assertThat(intVault.lookup(booleanKey)).isEqualTo(Some(false))
    assertThat(intVault.lookup(textKey)).isEqualTo(Some("0"))
    assertThat(textVault.lookup(textKey)).isEqualTo(Some("123"))
    assertThat(textVault.lookup(intKey)).isEqualTo(Some(123))
    assertThat(textVault.lookup(booleanKey)).isEqualTo(Some(true))
  }

  @Test
  def companionTypeClassInstancesAdaptKeysInGenericCode(): Unit = {
    final case class CountText(value: String)

    val insertContravariant: Contravariant[InsertKey] = InsertKey.ContravariantInsertKey
    val lookupFunctor: Functor[LookupKey] = LookupKey.FunctorLookupKey
    val rawKey: Key[Int] = newKey[Int]()

    val countTextInsertKey: InsertKey[CountText] = insertContravariant.contramap(rawKey)(count => count.value.toInt)
    val presentLookupKey: LookupKey[String] = lookupFunctor.as(rawKey, "present")
    val doubledLookupKey: LookupKey[Int] = lookupFunctor.map(rawKey)(_ * 2)

    val vault: Vault = Vault.empty.insert(countTextInsertKey, CountText("21"))

    assertThat(vault.lookup(rawKey)).isEqualTo(Some(21))
    assertThat(vault.lookup(presentLookupKey)).isEqualTo(Some("present"))
    assertThat(vault.lookup(doubledLookupKey)).isEqualTo(Some(42))
  }

  @Test
  def keyTypeClassInstancesUseUniqueTokensForHashingAndMapping(): Unit = {
    final case class Label(value: String)

    val firstKey: Key[String] = newKey[String]()
    val secondKey: Key[String] = newKey[String]()
    val labelKey: Key[Label] = Key.InvariantKey.imap(firstKey)(Label.apply)(_.value)
    val keyHash = Key.keyInstances[String]

    val vault: Vault = Vault.empty.insert(labelKey, Label("primary"))

    assertThat(firstKey).isNotEqualTo(secondKey)
    assertThat(keyHash.eqv(firstKey, firstKey)).isTrue
    assertThat(keyHash.eqv(firstKey, secondKey)).isFalse
    assertThat(keyHash.hash(firstKey)).isEqualTo(firstKey.hashCode())
    assertThat(vault.lookup(labelKey)).isEqualTo(Some(Label("primary")))
    assertThat(vault.lookup(firstKey)).isEqualTo(Some("primary"))
  }

  private def newKey[A](): Key[A] = Key.newKey[IO, A].unsafeRunSync()
}
