/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.skunk_core_3

import org.junit.jupiter.api.Assertions.{assertArrayEquals, assertEquals, assertFalse, assertThrows, assertTrue}
import org.junit.jupiter.api.Test
import scodec.bits.BitVector
import skunk.Codec
import skunk.Command
import skunk.Decoder
import skunk.Encoder
import skunk.Fragment
import skunk.Query
import skunk.RedactionStrategy
import skunk.SqlState
import skunk.Statement
import skunk.Void
import skunk.codec.all.*
import skunk.data.Arr
import skunk.data.Completion
import skunk.data.Encoded
import skunk.data.Identifier
import skunk.data.LTree
import skunk.data.Notification
import skunk.data.Type
import skunk.implicits.*

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneOffset
import java.util.UUID

class Skunk_core_3Test {
  @Test
  def scalarCodecsEncodeDecodeAndReportFailures(): Unit = {
    assertRoundTrip(int2, 7.toShort, "7")
    assertRoundTrip(int4, 42, "42")
    assertRoundTrip(int8, 123456789L, "123456789")
    assertRoundTrip(numeric(8, 2), BigDecimal("1234.56"), "1234.56")
    assertRoundTrip(float4, 1.25f, "1.25")
    assertRoundTrip(float8, 2.5d, "2.5")
    assertRoundTrip(bool, true, "t")
    assertRoundTrip(bool, false, "f")
    assertRoundTrip(varchar(20), "skunk", "skunk")
    assertRoundTrip(bpchar(4), "ab  ", "ab  ")
    assertRoundTrip(text, "hello", "hello")

    val invalidInt: Either[Decoder.Error, Int] = int4.decode(3, List(Some("not-an-int")))
    assertTrue(invalidInt.isLeft)
    assertEquals(Decoder.Error(3, 1, "Invalid: not-an-int"), invalidInt.swap.toOption.get)

    val nullText: Either[Decoder.Error, String] = text.decode(5, List(None))
    assertEquals(
      Decoder.Error(5, 1, "Unexpected NULL value in non-optional column."),
      nullText.swap.toOption.get
    )
  }

  @Test
  def binaryUuidAndTemporalCodecsRoundTripTextRepresentations(): Unit = {
    val bytes: Array[Byte] = Array[Byte](0, 1, 2, 15, 16, -1)
    val encodedBytes: List[Option[Encoded]] = bytea.encode(bytes)
    assertEquals(List(Some(Encoded("\\x0001020f10ff"))), encodedBytes)
    assertArrayEquals(bytes, bytea.decode(0, List(Some("\\x0001020f10ff"))).toOption.get)

    val bits: BitVector = BitVector.fromBin("101001").get
    assertRoundTrip(varbit(6), bits, "101001")

    val id: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    assertRoundTrip(uuid, id, "123e4567-e89b-12d3-a456-426614174000")

    assertRoundTrip(date, LocalDate.of(2024, 2, 29), "2024-02-29 AD")
    assertRoundTrip(time(3), LocalTime.of(12, 34, 56, 789000000), "12:34:56.789")
    assertRoundTrip(timetz(0), OffsetTime.of(8, 9, 10, 0, ZoneOffset.ofHours(2)), "08:09:10+02")
    assertRoundTrip(
      timestamp(6),
      LocalDateTime.of(2024, 3, 4, 5, 6, 7, 890000),
      "2024-03-04 05:06:07.00089 AD"
    )
    assertRoundTrip(
      timestamptz(0),
      OffsetDateTime.of(2024, 3, 4, 5, 6, 7, 0, ZoneOffset.UTC),
      "2024-03-04 05:06:07Z AD"
    )
    assertRoundTrip(interval, Duration.ofHours(2).plusMinutes(3), "PT2H3M")

    assertThrows(classOf[IllegalArgumentException], () => {
      time(7)
      ()
    })
    assertTrue(uuid.decode(0, List(Some("invalid-uuid"))).isLeft)
  }

  @Test
  def compositeCodecsMapOptionsAndRedactionBehaveConsistently(): Unit = {
    val pair: Codec[(String, Int)] = varchar.product(int4)
    assertEquals(List(Type.varchar, Type.int4), pair.types)
    assertEquals(List(Some(Encoded("Ada")), Some(Encoded("42"))), pair.encode(("Ada", 42)))
    assertEquals(Right(("Ada", 42)), pair.decode(0, List(Some("Ada"), Some("42"))))

    val ageCodec: Codec[Age] = int4.imap(Age.apply)(_.value)
    assertEquals(List(Some(Encoded("37"))), ageCodec.encode(Age(37)))
    assertEquals(Right(Age(37)), ageCodec.decode(0, List(Some("37"))))

    val positiveAgeCodec: Codec[Age] = int4.eimap { value =>
      Either.cond(value >= 0, Age(value), "age must be non-negative")
    }(_.value)
    assertEquals(Right(Age(1)), positiveAgeCodec.decode(0, List(Some("1"))))
    assertEquals(
      Decoder.Error(0, 1, "age must be non-negative"),
      positiveAgeCodec.decode(0, List(Some("-1"))).swap.toOption.get
    )

    val optionalPair: Codec[Option[(String, Int)]] = pair.opt
    assertEquals(List(None, None), optionalPair.encode(None))
    assertEquals(Right(None), optionalPair.decode(0, List(None, None)))
    assertEquals(Right(Some(("Bob", 5))), optionalPair.decode(0, List(Some("Bob"), Some("5"))))

    val redactedArguments: List[Option[Encoded]] = varchar.redacted.encode("secret")
    assertEquals(List(Some(Encoded("secret", redacted = true))), redactedArguments)
    assertEquals("?", redactedArguments.head.get.toString)
    assertEquals(List(Some(Encoded("secret"))), varchar.redacted.unredacted.encode("secret"))
    assertEquals(redactedArguments, RedactionStrategy.OptIn.redactArguments(redactedArguments))
    assertEquals(List(Some(Encoded("secret", redacted = true))), RedactionStrategy.All.redactArguments(varchar.encode("secret")))
    assertEquals(List(Some(Encoded("secret"))), RedactionStrategy.None.redactArguments(redactedArguments))
  }

  @Test
  def enumCodecsMapPostgresLabelsToDomainValues(): Unit = {
    val moodType: Type = Type("mood")
    val moodCodec: Codec[Mood] = `enum`[Mood](moodLabel, label => Mood.values.find(mood => moodLabel(mood) == label), moodType)

    assertEquals(List(moodType), moodCodec.types)
    assertEquals((2, "$1"), moodCodec.sql.run(1).value)
    assertEquals(List(Some(Encoded("curious"))), moodCodec.encode(Mood.Curious))
    assertEquals(Right(Mood.Happy), moodCodec.decode(0, List(Some("happy"))))
    assertEquals(
      Decoder.Error(4, 1, "mood: no such element 'angry'"),
      moodCodec.decode(4, List(Some("angry"))).swap.toOption.get
    )
  }

  @Test
  def encodersAndDecodersComposeAndRenderPlaceholders(): Unit = {
    val encoder: Encoder[(String, Int)] = varchar.product(int4)
    val valuesEncoder: Encoder[(String, Int)] = encoder.values
    val listEncoder: Encoder[List[Int]] = int4.list(3)
    val literalList: List[Int] = List(1, 2)
    val literalListEncoder: Encoder[literalList.type] = int4.list(literalList)

    assertEquals((3, "$1, $2"), encoder.sql.run(1).value)
    assertEquals((3, "($1, $2)"), valuesEncoder.sql.run(1).value)
    assertEquals((4, "$1, $2, $3"), listEncoder.sql.run(1).value)
    assertEquals(List(Some(Encoded("1")), Some(Encoded("2")), Some(Encoded("3"))), listEncoder.encode(List(1, 2, 3)))
    assertEquals((3, "$1, $2"), literalListEncoder.sql.run(1).value)
    assertEquals(List(Some(Encoded("1")), Some(Encoded("2"))), literalListEncoder.encode(literalList))

    val decoder: Decoder[(String, Int)] = varchar.asDecoder.product(int4.asDecoder)
    val textLengthDecoder: Decoder[Int] = varchar.asDecoder.map(_.length)
    val filteredDecoder: Decoder[Int] = int4.asDecoder.filter(_ > 10)
    val evenDecoder: Decoder[Int] = int4.asDecoder.emap(n => Either.cond(n % 2 == 0, n, "expected even value"))

    assertEquals(2, decoder.length)
    assertEquals(Right(("abc", 7)), decoder.decode(2, List(Some("abc"), Some("7"))))
    assertEquals(Right(5), textLengthDecoder.decode(0, List(Some("hello"))))
    assertEquals(Right(11), filteredDecoder.decode(0, List(Some("11"))))
    assertTrue(filteredDecoder.decode(0, List(Some("10"))).isLeft)
    assertEquals(Decoder.Error(0, 1, "expected even value"), evenDecoder.decode(0, List(Some("3"))).swap.toOption.get)
    assertEquals("Codec(varchar, int4)", encoder.toString)
    assertEquals("Decoder(varchar, int4)", decoder.toString)
  }

  @Test
  def fragmentsQueriesAndCommandsPreserveSqlTypesAndCacheKeys(): Unit = {
    val table: String = "person"
    val byAge: Fragment[Int] = sql"age > $int4"
    val active: Fragment[Void] = sql"active = true"
    val command: Command[Int] = sql"update #$table set active = false where $byAge and $active".command
    val query: Query[Int, String] = sql"select name from #$table where $byAge".query(varchar)
    val dynamicQuery: Query[Int, List[Option[String]]] = sql"select * from #$table where $byAge".queryDynamic

    assertEquals("age > $1", byAge.sql)
    assertEquals("update person set active = false where age > $1 and active = true", command.sql)
    assertEquals("select name from person where age > $1", query.sql)
    assertEquals("select * from person where age > $1", dynamicQuery.sql)
    assertEquals(List(Type.int4), command.encoder.types)
    assertEquals(Statement.CacheKey(command.sql, List(Type.int4), Nil), command.cacheKey)
    assertEquals(Statement.CacheKey(query.sql, List(Type.int4), List(Type.varchar)), query.cacheKey)
    assertEquals(Right(List(Some("a"), None)), dynamicQuery.decoder.decode(0, List(Some("a"), None)))

    val mappedQuery: Query[Age, Int] = query.dimap[Age, Int](_.value)(_.length)
    assertEquals(List(Some(Encoded("21"))), mappedQuery.encoder.encode(Age(21)))
    assertEquals(Right(3), mappedQuery.decoder.decode(0, List(Some("Bob"))))

    val mappedCommand: Command[Age] = command.contramap(_.value)
    assertEquals(List(Some(Encoded("21"))), mappedCommand.encoder.encode(Age(21)))

    val applied = byAge(18)
    assertEquals(byAge, applied.fragment)
    assertEquals(18, applied.argument)
    assertTrue(applied.toString.contains("AppledFragment"))
  }

  @Test
  def arraysIdentifiersLtreeAndTypesExposeValueSemantics(): Unit = {
    val parsed: Arr[Int] = Arr.parseWith(s => Right(s.toInt))("{{1,2},{3,4}}").toOption.get
    assertEquals(4, parsed.size)
    assertFalse(parsed.isEmpty)
    assertEquals(List(2, 2), parsed.dimensions)
    assertEquals(Some(3), parsed.get(1, 0))
    assertEquals(None, parsed.get(2, 0))
    assertEquals(List(1, 2, 3, 4), parsed.flattenTo(List))
    assertEquals(Some(List(4)), parsed.reshape(4).map(_.dimensions))
    assertEquals(None, parsed.reshape(3))
    assertEquals("{{\"1\",\"2\"},{\"3\",\"4\"}}", parsed.encode(_.toString))
    assertTrue(Arr.parse("{{a},{b,c}}").isLeft)

    val words: Arr[String] = Arr("a,b", "quote\"", "slash\\")
    assertEquals("{\"a,b\",\"quote\\\"\",\"slash\\\\\"}", words.encode(identity))
    assertEquals(List("a,b", "quote\"", "slash\\"), Arr.parse(words.encode(identity)).toOption.get.flattenTo(List))

    val identifier: Identifier = Identifier.fromString("valid_name_1").toOption.get
    assertEquals("valid_name_1", identifier.toString)
    assertTrue(Identifier.fromString("select").isLeft)
    assertTrue(Identifier.fromString("1_invalid").isLeft)

    val root: LTree = LTree.fromString("top.middle").toOption.get
    val child: LTree = LTree.fromLabels("top", "middle", "leaf").toOption.get
    assertTrue(root.isAncestorOf(child))
    assertTrue(child.isDescendantOf(root))
    assertEquals("top.middle.leaf", child.toString)
    assertTrue(LTree.fromString("bad-label").isLeft)

    val treeType: Type = Type("record", List(Type.int4, Type.varchar(10)))
    assertEquals("record { int4, varchar(10) }", treeType.toString)
    assertEquals("record(int4(),varchar(10)())", treeType.fold((name, children) => s"$name${children.mkString("(", ",", ")")}"))
    assertEquals(Type("node", List(Type("leaf"), Type("leaf"))), Type.unfold(0)(n => if n == 0 then List(1, 1) else Nil, n => if n == 0 then "node" else "leaf"))
  }

  @Test
  def completionsAndNotificationsModelServerResults(): Unit = {
    val updated: Completion = Completion.Update(3)
    val inserted: Completion = Completion.Insert(1)
    val unknown: Completion = Completion.Unknown("VACUUM")

    val updatedCount: Option[Int] = updated match {
      case Completion.Update(count) => Some(count)
      case _                        => None
    }
    val insertedCount: Option[Int] = inserted match {
      case Completion.Insert(count) => Some(count)
      case _                        => None
    }
    val unknownText: Option[String] = unknown match {
      case Completion.Unknown(text) => Some(text)
      case _                        => None
    }

    assertEquals(Some(3), updatedCount)
    assertEquals(Some(1), insertedCount)
    assertEquals(Some("VACUUM"), unknownText)
    assertEquals(Completion.Update(4), Completion.Update(3).copy(count = 4))

    val channel: Identifier = Identifier.fromString("events").toOption.get
    val notification: Notification[String] = Notification(1234, channel, "created")
    val mapped: Notification[Int] = notification.map(_.length)

    assertEquals(1234, mapped.pid)
    assertEquals(channel, mapped.channel)
    assertEquals(7, mapped.value)
    assertEquals(notification, notification.copy(value = "created"))
  }

  @Test
  def sqlStateEnumExposesStableCodesAndNames(): Unit = {
    assertEquals("23505", SqlState.UniqueViolation.code)
    assertEquals("42P01", SqlState.UndefinedTable.code)
    assertEquals("SuccessfulCompletion", SqlState.SuccessfulCompletion.toString)
    assertTrue(SqlState.values.toList.contains(SqlState.SyntaxError))
    assertEquals(Some(SqlState.ForeignKeyViolation), SqlState.valueOf("ForeignKeyViolation") match {
      case state if state.code == "23503" => Some(state)
      case _                              => None
    })
  }

  private def assertRoundTrip[A](codec: Codec[A], value: A, encoded: String): Unit = {
    assertEquals(List(Some(Encoded(encoded))), codec.encode(value))
    assertEquals(Right(value), codec.decode(0, List(Some(encoded))))
  }

  private final case class Age(value: Int)

  private enum Mood {
    case Happy, Curious, Sleepy
  }

  private def moodLabel(mood: Mood): String = mood match {
    case Mood.Happy   => "happy"
    case Mood.Curious => "curious"
    case Mood.Sleepy  => "sleepy"
  }
}
