/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.lenses_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import scalapb.lenses.Lens
import scalapb.lenses.Lens._
import scalapb.lenses.MessageLens
import scalapb.lenses.ObjectLens
import scalapb.lenses.Updatable

class Lenses_2_13Test {
  private final case class Address(street: String, city: String, postalCode: Int)

  private final case class Profile(
      name: String,
      age: Int,
      address: Address,
      alternateAddress: Option[Address],
      tags: Vector[String],
      flags: Set[String],
      scores: Map[String, Int]
  ) extends Updatable[Profile]

  private final class AddressMessageLens(root: Lens[Profile, Address]) extends MessageLens[Profile, Address](root)

  private val streetLens: Lens[Address, String] =
    Lens[Address, String](_.street)((address: Address, street: String) => address.copy(street = street))
  private val cityLens: Lens[Address, String] =
    Lens[Address, String](_.city)((address: Address, city: String) => address.copy(city = city))
  private val postalCodeLens: Lens[Address, Int] =
    Lens[Address, Int](_.postalCode)((address: Address, postalCode: Int) => address.copy(postalCode = postalCode))

  private val nameLens: Lens[Profile, String] =
    Lens[Profile, String](_.name)((profile: Profile, name: String) => profile.copy(name = name))
  private val ageLens: Lens[Profile, Int] =
    Lens[Profile, Int](_.age)((profile: Profile, age: Int) => profile.copy(age = age))
  private val addressLens: Lens[Profile, Address] =
    Lens[Profile, Address](_.address)((profile: Profile, address: Address) => profile.copy(address = address))
  private val alternateAddressLens: Lens[Profile, Option[Address]] =
    Lens[Profile, Option[Address]](_.alternateAddress) { (profile: Profile, alternateAddress: Option[Address]) =>
      profile.copy(alternateAddress = alternateAddress)
    }
  private val tagsLens: Lens[Profile, Vector[String]] =
    Lens[Profile, Vector[String]](_.tags)((profile: Profile, tags: Vector[String]) => profile.copy(tags = tags))
  private val flagsLens: Lens[Profile, Set[String]] =
    Lens[Profile, Set[String]](_.flags)((profile: Profile, flags: Set[String]) => profile.copy(flags = flags))
  private val scoresLens: Lens[Profile, Map[String, Int]] =
    Lens[Profile, Map[String, Int]](_.scores)((profile: Profile, scores: Map[String, Int]) => profile.copy(scores = scores))

  @Test
  def simpleLensSupportsGetSetModifyAndSetIfDefined(): Unit = {
    val profile: Profile = sampleProfile

    assertEquals("Ada", nameLens.get(profile))
    assertEquals(profile.copy(name = "Grace"), (nameLens := "Grace")(profile))
    assertEquals(profile.copy(age = 38), ageLens.modify(_ + 1)(profile))
    assertEquals(profile.copy(name = "Katherine"), nameLens.setIfDefined(Some("Katherine"))(profile))
    assertSame(profile, nameLens.setIfDefined(None)(profile))
  }

  @Test
  def composedZippedAndUnitLensesUpdateNestedAndWholeValues(): Unit = {
    val profile: Profile = sampleProfile
    val cityOnProfile: Lens[Profile, String] = addressLens.compose(cityLens)
    val nameAndAgeLens: Lens[Profile, (String, Int)] = nameLens.zip(ageLens)
    val replacement: Profile = profile.copy(name = "Replacement", age = 99)

    assertEquals("London", cityOnProfile.get(profile))
    assertEquals(profile.copy(address = profile.address.copy(city = "Oxford")), cityOnProfile.set("Oxford")(profile))
    assertEquals(("Ada", 37), nameAndAgeLens.get(profile))
    assertEquals(profile.copy(name = "Grace", age = 38), nameAndAgeLens.set(("Grace", 38))(profile))
    assertEquals(replacement, Lens.unit[Profile].set(replacement)(profile))
    assertEquals(profile.copy(age = 40), Lens.unit[Profile].modify(_.copy(age = 40))(profile))
  }

  @Test
  def unitLensActsAsIdentityForLensComposition(): Unit = {
    val profile: Profile = sampleProfile
    val unitThenNameLens: Lens[Profile, String] = Lens.unit[Profile].compose(nameLens)
    val nameThenUnitLens: Lens[Profile, String] = nameLens.compose(Lens.unit[String])

    assertEquals(nameLens.get(profile), unitThenNameLens.get(profile))
    assertEquals(nameLens.get(profile), nameThenUnitLens.get(profile))
    assertEquals(profile.copy(name = "Grace"), unitThenNameLens.set("Grace")(profile))
    assertEquals(profile.copy(name = "adA"), nameThenUnitLens.modify(_.reverse)(profile))
  }

  @Test
  def objectLensBuildsNestedFieldsAndAppliesBatchUpdates(): Unit = {
    val profile: Profile = sampleProfile
    val addressObjectLens: ObjectLens[Profile, Address] = new ObjectLens[Profile, Address](addressLens)
    val messageLens: MessageLens[Profile, Address] = new AddressMessageLens(addressLens)
    val cityFromExistingLens: Lens[Profile, String] = addressObjectLens.field(cityLens)
    val postalCodeFromFunctions: Lens[Profile, Int] = addressObjectLens.field((address: Address) => address.postalCode) {
      (address: Address, postalCode: Int) => address.copy(postalCode = postalCode)
    }

    assertEquals("London", cityFromExistingLens.get(profile))
    assertEquals(profile.copy(address = profile.address.copy(postalCode = 90210)), postalCodeFromFunctions.set(90210)(profile))
    assertEquals(profile.address, messageLens.get(profile))

    val updated: Profile = addressObjectLens.update(
      (root: Lens[Address, Address]) => root.compose(streetLens) := "Bletchley Park",
      (root: Lens[Address, Address]) => root.compose(cityLens).modify(_.toUpperCase),
      (root: Lens[Address, Address]) => root.compose(postalCodeLens).modify(_ + 1)
    )(profile)

    assertEquals(
      profile.copy(address = Address(street = "Bletchley Park", city = "LONDON", postalCode = 12346)),
      updated
    )
  }

  @Test
  def updatableAppliesRootLensTransformationsInOrder(): Unit = {
    val profile: Profile = sampleProfile

    val updated: Profile = profile.update(
      (root: Lens[Profile, Profile]) => root.compose(nameLens) := "Grace",
      (root: Lens[Profile, Profile]) => root.compose(ageLens).modify(_ + 2),
      (root: Lens[Profile, Profile]) => root.compose(addressLens).compose(cityLens) := "Paris"
    )

    assertEquals(profile.copy(name = "Grace", age = 39, address = profile.address.copy(city = "Paris")), updated)
  }

  @Test
  def optionLensTransformsOnlyDefinedValues(): Unit = {
    val profile: Profile = sampleProfile
    val withoutAlternateAddress: Profile = profile.copy(alternateAddress = None)

    val updated: Profile = alternateAddressLens.inplaceMap { address: Lens[Address, Address] =>
      address.compose(cityLens).modify(city => s"$city, France")
    }(profile)
    val unchanged: Profile = alternateAddressLens.inplaceMap { address: Lens[Address, Address] =>
      address.compose(cityLens) := "Nowhere"
    }(withoutAlternateAddress)

    assertEquals(
      profile.copy(alternateAddress = Some(profile.alternateAddress.get.copy(city = "Paris, France"))),
      updated
    )
    assertEquals(withoutAlternateAddress, unchanged)
  }

  @Test
  def sequenceLensIndexesAppendsAndMapsElements(): Unit = {
    val profile: Profile = sampleProfile

    assertEquals("native-image", tagsLens(1).get(profile))
    assertEquals(profile.copy(tags = Vector("compiler", "native-image", "metadata")), (tagsLens :+= "metadata")(profile))
    assertEquals(
      profile.copy(tags = Vector("compiler", "native-image", "lenses", "protobuf")),
      (tagsLens :++= Vector("lenses", "protobuf"))(profile)
    )
    assertEquals(profile.copy(tags = Vector("scala", "native-image")), (tagsLens.head := "scala")(profile))
    assertEquals(profile.copy(tags = Vector("compiler", "graalvm")), (tagsLens.last := "graalvm")(profile))
    assertEquals(profile.copy(tags = Vector("COMPILER", "NATIVE-IMAGE")), tagsLens.foreach(_.modify(_.toUpperCase))(profile))
  }

  @Test
  def setLensAddsManyValuesAndTransformsElements(): Unit = {
    val profile: Profile = sampleProfile

    assertEquals(profile.copy(flags = Set("fast", "deterministic", "covered")), (flagsLens :+= "covered")(profile))
    assertEquals(
      profile.copy(flags = Set("fast", "deterministic", "native", "jvm")),
      (flagsLens :++= List("native", "jvm"))(profile)
    )
    assertEquals(profile.copy(flags = Set("fast!", "deterministic!")), flagsLens.foreach(_.modify(_ + "!"))(profile))
  }

  @Test
  def collectionLensesAppendFromOneShotIterableOnceSources(): Unit = {
    val profile: Profile = sampleProfile

    assertEquals(
      profile.copy(tags = Vector("compiler", "native-image", "agent", "metadata")),
      (tagsLens :++= Iterator("agent", "metadata"))(profile)
    )
    assertEquals(
      profile.copy(flags = Set("fast", "deterministic", "native", "jvm")),
      (flagsLens :++= Iterator("native", "jvm"))(profile)
    )
  }

  @Test
  def mapLensAccessesAddsAndTransformsEntriesAndValues(): Unit = {
    val profile: Profile = sampleProfile

    assertEquals(100, scoresLens("math").get(profile))
    assertEquals(profile.copy(scores = Map("math" -> 101, "science" -> 98)), scoresLens("math").modify(_ + 1)(profile))
    assertEquals(profile.copy(scores = Map("math" -> 100, "science" -> 98, "art" -> 77)), (scoresLens :+= ("art" -> 77))(profile))
    assertEquals(
      profile.copy(scores = Map("math" -> 100, "science" -> 98, "art" -> 77, "music" -> 88)),
      (scoresLens :++= Map("art" -> 77, "music" -> 88))(profile)
    )
    assertEquals(
      profile.copy(scores = Map("MATH" -> 101, "SCIENCE" -> 99)),
      scoresLens.foreach(_.modify { case (key: String, value: Int) => (key.toUpperCase, value + 1) })(profile)
    )
    assertEquals(profile.copy(scores = Map("math" -> 200, "science" -> 196)), scoresLens.foreachValue(_.modify(_ * 2))(profile))
    assertEquals(profile.copy(scores = Map("math" -> 90, "science" -> 88)), scoresLens.mapValues(_ - 10)(profile))
  }

  private def sampleProfile: Profile = Profile(
    name = "Ada",
    age = 37,
    address = Address(street = "Main Street", city = "London", postalCode = 12345),
    alternateAddress = Some(Address(street = "Rue Cler", city = "Paris", postalCode = 75007)),
    tags = Vector("compiler", "native-image"),
    flags = Set("fast", "deterministic"),
    scores = Map("math" -> 100, "science" -> 98)
  )
}
