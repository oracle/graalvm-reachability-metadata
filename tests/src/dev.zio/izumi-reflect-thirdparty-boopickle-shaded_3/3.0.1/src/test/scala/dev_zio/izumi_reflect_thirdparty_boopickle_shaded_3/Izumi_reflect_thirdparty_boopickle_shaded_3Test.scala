/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.izumi_reflect_thirdparty_boopickle_shaded_3

import izumi.reflect.Tag
import izumi.reflect.macrortti.LightTypeTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Izumi_reflect_thirdparty_boopickle_shaded_3Test {
  @Test
  def publicLightTypeTagSerializationRoundTripsThroughShadedBoopickle(): Unit = {
    val tag: LightTypeTag = Tag[Map[String, List[Option[FixturePayload]]]].tag

    val serialized: LightTypeTag.Serialized = tag.serialize()
    val parsed: LightTypeTag = LightTypeTag.parse(serialized)

    assertEquals(LightTypeTag.currentBinaryFormatVersion, serialized.version)
    assertFalse(serialized.ref.isEmpty)
    assertFalse(serialized.databases.isEmpty)
    assertEquals(tag, parsed)
    assertEquals(tag.repr, parsed.repr)
    assertTrue(parsed =:= tag)
  }

  @Test
  def parsedLightTypeTagKeepsPublicSubtypeEvidence(): Unit = {
    val listTag: LightTypeTag = Tag[List[String]].tag
    val seqTag: LightTypeTag = Tag[Seq[String]].tag

    val parsedListTag: LightTypeTag = LightTypeTag.parse(listTag.serialize())
    val parsedSeqTag: LightTypeTag = LightTypeTag.parse(seqTag.serialize())

    assertTrue(parsedListTag <:< parsedSeqTag)
    assertFalse(parsedSeqTag <:< parsedListTag)
  }
}

final case class FixturePayload(id: Int, name: String)
