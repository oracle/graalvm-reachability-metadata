/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_core_3

import com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.github.plokhotnyuk.jsoniter_scala.macros.CodecMakerConfig
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.collection.immutable.ArraySeq
import scala.collection.immutable.BitSet
import scala.collection.immutable.IntMap
import scala.collection.immutable.ListMap
import scala.collection.immutable.LongMap
import scala.collection.immutable.SortedSet
import scala.collection.immutable.TreeMap
import scala.collection.mutable

import AccessPolicyCollectionsTest.*

/**
 * Scala collection families and their size limits for untrusted input: an access-policy
 * service exchanges permission bit sets, `IntMap`/`LongMap` quotas, insertion-ordered and
 * sorted maps, sorted and mutable sets, `ArraySeq`/`Vector`/`List` sequences, nested arrays
 * and maps encoded as arrays of pairs, and rejects payloads that exceed its bit-set value,
 * map-insert and set-insert limits.
 */
class AccessPolicyCollectionsTest {

  private val readerWithoutDump: ReaderConfig = ReaderConfig.withAppendHexDumpToParseException(false)

  private val policy: Policy = Policy(
    permissions = BitSet(0, 3, 64, 130),
    quotasByTenant = IntMap(7 -> 100, 2 -> 25),
    sessionsByUser = LongMap(9000000000L -> "s-1", 42L -> "s-2"),
    rulesInOrder = ListMap("deny-guests" -> false, "allow-admins" -> true, "audit-all" -> true),
    rateLimits = TreeMap("write" -> 10, "read" -> 500, "delete" -> 1),
    regions = SortedSet("us-east", "eu-west", "ap-south"),
    hosts = ArraySeq("a.example", "b.example"),
    tiers = Vector(1, 2, 3),
    owners = List("ops", "security"),
    matrix = Array(Array(1, 0), Array(0, 1)),
    tags = mutable.LinkedHashSet("pci", "gdpr"),
    overrides = mutable.LinkedHashMap("alice" -> 3, "bob" -> 1)
  )

  @Test
  def everyCollectionFamilyRoundTripsWithItsNaturalJsonShape(): Unit = {
    val json: String = writeToString(policy)

    assertEquals(
      """{"permissions":[0,3,64,130],"quotasByTenant":{"2":25,"7":100},""" +
        """"sessionsByUser":{"42":"s-2","9000000000":"s-1"},""" +
        """"rulesInOrder":{"deny-guests":false,"allow-admins":true,"audit-all":true},""" +
        """"rateLimits":{"delete":1,"read":500,"write":10},""" +
        """"regions":["ap-south","eu-west","us-east"],"hosts":["a.example","b.example"],""" +
        """"tiers":[1,2,3],"owners":["ops","security"],"matrix":[[1,0],[0,1]],""" +
        """"tags":["pci","gdpr"],"overrides":{"alice":3,"bob":1}}""",
      json
    )
    val decoded: Policy = readFromString[Policy](json)
    assertEquals(policy.copy(matrix = null), decoded.copy(matrix = null))
    assertEquals(policy.matrix.map(_.toSeq).toSeq, decoded.matrix.map(_.toSeq).toSeq)
    assertEquals(List("deny-guests", "allow-admins", "audit-all"), decoded.rulesInOrder.keys.toList)
    assertEquals(List("delete", "read", "write"), decoded.rateLimits.keys.toList)
    assertTrue(decoded.permissions.contains(130))
  }

  @Test
  def clientsMaySendSortedCollectionsInAnyOrder(): Unit = {
    val decoded: Policy = readFromString[Policy](
      """{"permissions":[130,3,0],"quotasByTenant":{},"sessionsByUser":{},"rulesInOrder":{},""" +
        """"rateLimits":{"write":1,"admin":2},"regions":["z","a","m","a"],"hosts":[],"tiers":[],""" +
        """"owners":[],"matrix":[],"tags":["x","x","y"],"overrides":{"bob":1,"bob":2}}"""
    )

    assertEquals(BitSet(0, 3, 130), decoded.permissions)
    assertEquals(List("admin", "write"), decoded.rateLimits.keys.toList)
    assertEquals(List("a", "m", "z"), decoded.regions.toList)
    assertEquals(List("x", "y"), decoded.tags.toList)
    assertEquals(mutable.LinkedHashMap("bob" -> 2), decoded.overrides)
    assertEquals(0, decoded.matrix.length)
  }

  @Test
  def mapsWithStructuredKeysTravelAsArraysOfPairs(): Unit = {
    val grants: Grants = Grants(
      Map(Resource("db", 1) -> SortedSet("read"), Resource("queue", 7) -> SortedSet("publish", "consume"))
    )

    val json: String = writeToString(grants)

    assertTrue(json.startsWith("""{"byResource":[["""), json)
    assertTrue(json.contains("""[{"kind":"queue","id":7},["consume","publish"]]"""), json)
    assertTrue(json.contains("""[{"kind":"db","id":1},["read"]]"""), json)
    assertEquals(grants, readFromString[Grants](json))
  }

  @Test
  def bitSetValuesBeyondTheLimitAreRejected(): Unit = {
    val error: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Flags]("""{"enabled":[1,255,256]}""", readerWithoutDump)
    )
    val negative: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Flags]("""{"enabled":[-1]}""", readerWithoutDump)
    )

    assertEquals("illegal value for bit set, offset: 0x00000014", error.getMessage)
    assertEquals("illegal value for bit set, offset: 0x0000000d", negative.getMessage)
    assertEquals(Flags(BitSet(1, 255)), readFromString[Flags]("""{"enabled":[255,1]}"""))
  }

  @Test
  def mapsWithTooManyEntriesAreRejected(): Unit = {
    val entries: Int => String = (count: Int) => (1 to count).map(i => s""""k$i":$i""").mkString(",")
    val error: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Bounded](s"""{"limits":{${entries(5)}},"members":[]}""", readerWithoutDump)
    )

    assertTrue(error.getMessage.startsWith("too many map inserts, offset: "), error.getMessage)
    val accepted: Bounded = readFromString[Bounded](s"""{"limits":{${entries(4)}},"members":[]}""")
    assertEquals(4, accepted.limits.size)
    assertEquals(4, accepted.limits("k4"))
  }

  @Test
  def setsWithTooManyElementsAreRejected(): Unit = {
    val error: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Bounded]("""{"limits":{},"members":["a","b","c","d"]}""", readerWithoutDump)
    )

    assertTrue(error.getMessage.startsWith("too many set inserts, offset: "), error.getMessage)
    assertEquals(
      Bounded(Map.empty, Set("a", "b", "c")),
      readFromString[Bounded]("""{"limits":{},"members":["a","b","c"]}""")
    )
  }

  @Test
  def nonNumericKeysOfIntegerKeyedMapsAreRejected(): Unit = {
    val error: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Policy]("""{"quotasByTenant":{"seven":1}}""", readerWithoutDump)
    )

    assertTrue(error.getMessage.startsWith("illegal number, offset: "), error.getMessage)
  }
}

object AccessPolicyCollectionsTest {

  final case class Policy(
    permissions: BitSet,
    quotasByTenant: IntMap[Int],
    sessionsByUser: LongMap[String],
    rulesInOrder: ListMap[String, Boolean],
    rateLimits: TreeMap[String, Int],
    regions: SortedSet[String],
    hosts: ArraySeq[String],
    tiers: Vector[Int],
    owners: List[String],
    matrix: Array[Array[Int]],
    tags: mutable.LinkedHashSet[String],
    overrides: mutable.LinkedHashMap[String, Int]
  )

  final case class Resource(kind: String, id: Int)

  final case class Grants(byResource: Map[Resource, SortedSet[String]])

  final case class Flags(enabled: BitSet)

  final case class Bounded(limits: Map[String, Int], members: Set[String])

  /** Uses the default limits for every collection field. */
  given policyCodec: JsonValueCodec[Policy] = JsonCodecMaker.make

  /** Encodes maps whose keys are objects as JSON arrays of `[key, value]` pairs. */
  given grantsCodec: JsonValueCodec[Grants] = JsonCodecMaker.make(CodecMakerConfig.withMapAsArray(true))

  /** Accepts feature flags numbered 0 to 255 only. */
  given flagsCodec: JsonValueCodec[Flags] = JsonCodecMaker.make(CodecMakerConfig.withBitSetValueLimit(256))

  /** Caps untrusted maps at 4 entries and sets at 3 elements. */
  given boundedCodec: JsonValueCodec[Bounded] =
    JsonCodecMaker.make(CodecMakerConfig.withMapMaxInsertNumber(4).withSetMaxInsertNumber(3))
}
