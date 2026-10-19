/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_core_3

import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonWriter
import com.github.plokhotnyuk.jsoniter_scala.core.ReaderConfig
import com.github.plokhotnyuk.jsoniter_scala.core.WriterConfig
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.collection.immutable.VectorMap

import ConfigDocumentTreeTest.*

/**
 * Schema-less dynamic JSON document trees decoded and encoded by token-type dispatch: a
 * configuration service parses arbitrary JSON into an untyped tree that keeps key order and
 * exact decimals, applies RFC 7396 merge patches, renders the result compactly and pretty,
 * binds a subtree to a typed section, and rejects nesting bombs and duplicated keys.
 */
class ConfigDocumentTreeTest {

  private val baseDocument: String =
    """{
      |  "service": "billing",
      |  "enabled": true,
      |  "replicas": 3,
      |  "ratio": 0.75,
      |  "budget": 12345678901234567890.125,
      |  "owner": null,
      |  "tags": ["eu", "pci", "caf\u00e9"],
      |  "database": {"url": "jdbc:postgresql://db:5432/billing", "pool": {"min": 2, "max": 16}},
      |  "features": {}
      |}""".stripMargin

  private val strict: ReaderConfig = ReaderConfig.withAppendHexDumpToParseException(false)

  @Test
  def arbitraryDocumentIsDecodedIntoAnOrderedTreeWithExactNumbers(): Unit = {
    val tree: Json = readFromString[Json](baseDocument)

    val expected: Json = Json.Obj(
      VectorMap(
        "service" -> Json.Str("billing"),
        "enabled" -> Json.Bool(true),
        "replicas" -> Json.Num(BigDecimal(3)),
        "ratio" -> Json.Num(BigDecimal("0.75")),
        "budget" -> Json.Num(BigDecimal("12345678901234567890.125")),
        "owner" -> Json.Null,
        "tags" -> Json.Arr(Vector(Json.Str("eu"), Json.Str("pci"), Json.Str("caf\u00e9"))),
        "database" -> Json.Obj(
          VectorMap(
            "url" -> Json.Str("jdbc:postgresql://db:5432/billing"),
            "pool" -> Json.Obj(VectorMap("min" -> Json.Num(BigDecimal(2)), "max" -> Json.Num(BigDecimal(16))))
          )
        ),
        "features" -> Json.Obj(VectorMap.empty)
      )
    )
    assertEquals(expected, tree)
    assertEquals(
      List("service", "enabled", "replicas", "ratio", "budget", "owner", "tags", "database", "features"),
      tree.keys
    )
    assertEquals(Some(Json.Num(BigDecimal(16))), tree.at("database", "pool", "max"))
    assertEquals(None, tree.at("database", "pool", "idle"))

    assertEquals(Json.Null, readFromString[Json]("null"))
    assertEquals(Json.Arr(Vector.empty), readFromString[Json](" [ ] "))
    assertEquals(Json.Num(BigDecimal("-1.5E+3")), readFromString[Json]("-1.5e3"))
  }

  @Test
  def treeIsRenderedCompactlyAndPrettyAndRoundTrips(): Unit = {
    val tree: Json = readFromString[Json](baseDocument)

    val compact: String = writeToString(tree)
    assertEquals(
      """{"service":"billing","enabled":true,"replicas":3,"ratio":0.75,""" +
        """"budget":12345678901234567890.125,"owner":null,"tags":["eu","pci",""" +
        "\"caf\u00e9\"]," +
        """"database":{"url":"jdbc:postgresql://db:5432/billing","pool":{"min":2,"max":16}},""" +
        """"features":{}}""",
      compact
    )
    assertEquals(tree, readFromString[Json](compact))

    val section: Json = tree.at("database").get
    assertEquals(
      """{
        |  "url": "jdbc:postgresql://db:5432/billing",
        |  "pool": {
        |    "min": 2,
        |    "max": 16
        |  }
        |}""".stripMargin,
      writeToString(section, WriterConfig.withIndentionStep(2))
    )
    val pretty: String = writeToString(tree, WriterConfig.withIndentionStep(4))
    assertTrue(pretty.contains("\n    \"tags\": [\n        \"eu\",\n"), pretty)
    assertEquals(tree, readFromString[Json](pretty))
  }

  @Test
  def mergePatchOverridesRemovesAndAddsSettings(): Unit = {
    val base: Json = readFromString[Json](baseDocument)
    val patch: Json = readFromString[Json](
      """{"replicas":5,"owner":"team-payments","ratio":null,"tags":["us"],
        |"database":{"pool":{"max":32,"min":null},"readOnly":false},"features":{"beta":{"tier":"gold"}}}""".stripMargin
    )

    val merged: Json = base.mergePatch(patch)

    assertEquals(
      """{"service":"billing","enabled":true,"replicas":5,"budget":12345678901234567890.125,""" +
        """"owner":"team-payments","tags":["us"],""" +
        """"database":{"url":"jdbc:postgresql://db:5432/billing","pool":{"max":32},"readOnly":false},""" +
        """"features":{"beta":{"tier":"gold"}}}""",
      writeToString(merged)
    )
    assertEquals(base, readFromString[Json](baseDocument))
    assertEquals(Json.Str("replaced"), base.mergePatch(Json.Str("replaced")))
  }

  @Test
  def subtreeIsBoundToATypedSection(): Unit = {
    val merged: Json = readFromString[Json](baseDocument)
      .mergePatch(readFromString[Json]("""{"database":{"pool":{"max":32}}}"""))

    val database: Database = readFromString[Database](writeToString(merged.at("database").get))

    assertEquals(Database("jdbc:postgresql://db:5432/billing", Pool(2, 32)), database)
    assertEquals(readFromString[Json](writeToString(database)), merged.at("database").get)
  }

  @Test
  def hostileDocumentsAreRejected(): Unit = {
    val bomb: String = "[" * (MaxDepth + 1) + "]" * (MaxDepth + 1)
    val tooDeep: JsonReaderException =
      assertThrows(classOf[JsonReaderException], () => readFromString[Json](bomb, strict))
    assertTrue(tooDeep.getMessage.startsWith("nesting deeper than 32 levels"), tooDeep.getMessage)
    val nested: String = "[" * MaxDepth + "]" * MaxDepth
    assertEquals(nested, writeToString(readFromString[Json](nested)))

    val duplicated: JsonReaderException = assertThrows(
      classOf[JsonReaderException],
      () => readFromString[Json]("""{"replicas":3,"replicas":4}""", strict)
    )
    assertTrue(duplicated.getMessage.startsWith("duplicated field \"replicas\""), duplicated.getMessage)

    val unknownToken: JsonReaderException =
      assertThrows(classOf[JsonReaderException], () => readFromString[Json]("""{"a":@}""", strict))
    assertTrue(unknownToken.getMessage.startsWith("expected JSON value"), unknownToken.getMessage)

    val unclosed: JsonReaderException =
      assertThrows(classOf[JsonReaderException], () => readFromString[Json]("""{"a":[1,2}""", strict))
    assertTrue(unclosed.getMessage.startsWith("expected ']' or ','"), unclosed.getMessage)

    val trailing: JsonReaderException =
      assertThrows(classOf[JsonReaderException], () => readFromString[Json]("""{"a":1} {""", strict))
    assertTrue(trailing.getMessage.startsWith("expected end of input"), trailing.getMessage)
  }
}

object ConfigDocumentTreeTest {

  /** Deepest array or object nesting the service accepts from untrusted callers. */
  val MaxDepth: Int = 32

  /** Untyped JSON value; objects keep their keys in document order. */
  sealed trait Json {

    def keys: List[String] = this match {
      case Json.Obj(fields) => fields.keys.toList
      case _ => Nil
    }

    def at(path: String*): Option[Json] = path.foldLeft(Option(this)) {
      case (Some(Json.Obj(fields)), key) => fields.get(key)
      case _ => None
    }

    /** Applies an RFC 7396 JSON merge patch: `null` removes a key, objects merge recursively. */
    def mergePatch(patch: Json): Json = (this, patch) match {
      case (Json.Obj(target), Json.Obj(changes)) =>
        Json.Obj(changes.foldLeft(target) {
          case (acc, (key, Json.Null)) => acc - key
          case (acc, (key, change)) => acc.updated(key, acc.getOrElse(key, Json.Null).mergePatch(change))
        })
      case (_, Json.Obj(_)) => Json.Obj(VectorMap.empty).mergePatch(patch)
      case _ => patch
    }
  }

  object Json {
    case object Null extends Json
    final case class Bool(value: Boolean) extends Json
    final case class Num(value: BigDecimal) extends Json
    final case class Str(value: String) extends Json
    final case class Arr(items: Vector[Json]) extends Json
    final case class Obj(fields: VectorMap[String, Json]) extends Json
  }

  given JsonValueCodec[Json] = new JsonValueCodec[Json] {

    override def nullValue: Json = Json.Null

    override def decodeValue(in: JsonReader, default: Json): Json = decode(in, 0)

    override def encodeValue(x: Json, out: JsonWriter): Unit = x match {
      case Json.Null => out.writeNull()
      case Json.Bool(value) => out.writeVal(value)
      case Json.Num(value) => out.writeVal(value)
      case Json.Str(value) => out.writeVal(value)
      case Json.Arr(items) =>
        out.writeArrayStart()
        items.foreach(item => encodeValue(item, out))
        out.writeArrayEnd()
      case Json.Obj(fields) =>
        out.writeObjectStart()
        fields.foreach { (key: String, value: Json) =>
          out.writeKey(key)
          encodeValue(value, out)
        }
        out.writeObjectEnd()
    }

    private def decode(in: JsonReader, depth: Int): Json = {
      val token: Byte = in.nextToken()
      if (token == '"') {
        in.rollbackToken()
        Json.Str(in.readString(null))
      } else if (token == 't' || token == 'f') {
        in.rollbackToken()
        Json.Bool(in.readBoolean())
      } else if (token == 'n') {
        in.readNullOrError(Json.Null, "expected JSON value")
      } else if ((token >= '0' && token <= '9') || token == '-') {
        in.rollbackToken()
        Json.Num(in.readBigDecimal(null))
      } else if (token == '[') {
        checkDepth(in, depth)
        val items = Vector.newBuilder[Json]
        if (!in.isNextToken(']')) {
          in.rollbackToken()
          items += decode(in, depth + 1)
          while (in.isNextToken(',')) items += decode(in, depth + 1)
          if (!in.isCurrentToken(']')) in.arrayEndOrCommaError()
        }
        Json.Arr(items.result())
      } else if (token == '{') {
        checkDepth(in, depth)
        var fields: VectorMap[String, Json] = VectorMap.empty
        if (!in.isNextToken('}')) {
          in.rollbackToken()
          fields = readField(in, fields, depth)
          while (in.isNextToken(',')) fields = readField(in, fields, depth)
          if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
        }
        Json.Obj(fields)
      } else {
        in.decodeError("expected JSON value")
      }
    }

    private def readField(in: JsonReader, fields: VectorMap[String, Json], depth: Int): VectorMap[String, Json] = {
      val key: String = in.readKeyAsString()
      if (fields.contains(key)) in.decodeError(s"duplicated field \"$key\"")
      fields.updated(key, decode(in, depth + 1))
    }

    private def checkDepth(in: JsonReader, depth: Int): Unit =
      if (depth >= MaxDepth) in.decodeError(s"nesting deeper than $MaxDepth levels")
  }

  final case class Pool(min: Int, max: Int)

  final case class Database(url: String, pool: Pool)

  given JsonValueCodec[Database] = JsonCodecMaker.make
}
