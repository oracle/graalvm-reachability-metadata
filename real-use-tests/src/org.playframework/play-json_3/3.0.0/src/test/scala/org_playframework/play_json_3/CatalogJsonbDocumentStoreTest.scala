/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_json_3

import java.net.ServerSocket
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID
import java.util.concurrent.TimeUnit

import scala.collection.mutable.ListBuffer
import scala.util.Using

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsPath
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat

/**
 * Persisting play-json documents in a PostgreSQL `jsonb` column through JDBC.
 *
 * A product-catalog service keeps each item as a JSON document: it serializes items with
 * `Json.stringify`, binds the text as `jsonb`, and filters with containment queries whose
 * criteria it builds with `Json.obj`. It applies partial updates by deep-merging a patch into
 * the stored document, reads aggregated `jsonb_agg` arrays back into case classes, and reports
 * the paths of a legacy row whose document no longer matches the current format.
 */
class CatalogJsonbDocumentStoreTest {
  import CatalogJsonbDocumentStoreTest.*

  @Test
  def storesQueriesPatchesAndValidatesCatalogDocuments(): Unit = {
    val lantern: CatalogItem = CatalogItem(
      sku = "LAN-100",
      name = "Storm Lantern",
      priceCents = 2499L,
      tags = List("outdoor", "lighting"),
      dimensions = Dimensions(12.5, 30.0),
      stock = Map("berlin" -> 14, "lyon" -> 3)
    )
    val tent: CatalogItem = CatalogItem(
      sku = "TNT-200",
      name = "Ridge Tent",
      priceCents = 18900L,
      tags = List("outdoor", "camping"),
      dimensions = Dimensions(210.0, 120.0),
      stock = Map("berlin" -> 2)
    )
    val desk: CatalogItem = CatalogItem(
      sku = "DSK-300",
      name = "Standing Desk",
      priceCents = 45000L,
      tags = List("office"),
      dimensions = Dimensions(140.0, 75.5),
      stock = Map.empty
    )

    startPostgres()
    try Using.resource(openConnection()) { connection =>
      execute(connection, "CREATE TABLE catalog_items (sku text PRIMARY KEY, doc jsonb NOT NULL)")
      List(lantern, tent, desk).foreach(item => save(connection, item))

      assertEquals(Some(tent), load(connection, "TNT-200").map(_.as[CatalogItem]))
      assertEquals(
        Json.toJson(lantern),
        load(connection, "LAN-100").getOrElse(JsObject.empty),
        "jsonb reorders keys but the parsed document stays equal"
      )

      val outdoorInBerlin: JsObject = Json.obj(
        "tags" -> Json.arr("outdoor"),
        "stock" -> Json.obj("berlin" -> 2)
      )
      assertEquals(List(tent), findContaining(connection, outdoorInBerlin))
      assertEquals(
        List(lantern, tent),
        findContaining(connection, Json.obj("tags" -> Json.arr("outdoor")))
      )

      val restock: JsObject = Json.obj(
        "priceCents" -> 2299,
        "stock" -> Json.obj("lyon" -> 20, "porto" -> 5)
      )
      val stored: JsObject = load(connection, "LAN-100").getOrElse(JsObject.empty)
      val patched: JsObject = stored.deepMerge(restock)
      replace(connection, "LAN-100", patched)

      val reloaded: JsObject = load(connection, "LAN-100").getOrElse(JsObject.empty)
      assertEquals(
        JsSuccess(Map("berlin" -> 14, "lyon" -> 20, "porto" -> 5)),
        (reloaded \ "stock").validate[Map[String, Int]]
      )
      assertEquals(Some(JsString("Storm Lantern")), (reloaded \ "name").toOption)
      assertEquals(
        lantern.copy(priceCents = 2299L, stock = Map("berlin" -> 14, "lyon" -> 20, "porto" -> 5)),
        reloaded.as[CatalogItem]
      )

      val names: List[String] = queryStrings(
        connection,
        "SELECT doc ->> 'name' FROM catalog_items WHERE (doc -> 'dimensions' ->> 'widthCm')::numeric > 100 ORDER BY sku"
      )
      assertEquals(List("Standing Desk", "Ridge Tent"), names)

      val aggregated: JsValue = Json.parse(
        queryStrings(connection, "SELECT jsonb_agg(doc ORDER BY sku) FROM catalog_items").head
      )
      val catalog: JsResult[List[CatalogItem]] = aggregated.validate[List[CatalogItem]]
      assertEquals(List("DSK-300", "LAN-100", "TNT-200"), catalog.get.map(_.sku))
      assertEquals(
        List("office", "outdoor", "lighting", "outdoor", "camping"),
        (aggregated \\ "tags").toList.flatMap(_.as[List[String]])
      )

      execute(
        connection,
        """INSERT INTO catalog_items (sku, doc) VALUES ('OLD-001',
          |'{"sku": "OLD-001", "name": "Legacy Stove", "price": 3100, "tags": "kitchen",
          |  "dimensions": {"widthCm": "wide", "heightCm": 40}, "stock": {}}')""".stripMargin
      )
      val legacy: JsResult[CatalogItem] = load(connection, "OLD-001").getOrElse(JsObject.empty).validate[CatalogItem]
      legacy match {
        case JsError(errors) =>
          val paths: Set[JsPath] = errors.map(_._1).toSet
          assertEquals(
            Set(
              JsPath \ "priceCents",
              JsPath \ "tags",
              JsPath \ "dimensions" \ "widthCm"
            ),
            paths
          )
        case JsSuccess(value, _) =>
          throw new AssertionError(s"legacy document unexpectedly parsed as $value")
      }

      val emptyStock: JsObject = load(connection, "DSK-300").getOrElse(JsObject.empty)
      assertEquals(Some(JsObject.empty), (emptyStock \ "stock").toOption)
      assertTrue((emptyStock \ "stock" \ "berlin").isEmpty)
    }
    finally stopPostgres()
  }
}

object CatalogJsonbDocumentStoreTest {

  final case class Dimensions(widthCm: Double, heightCm: Double)

  final case class CatalogItem(
      sku: String,
      name: String,
      priceCents: Long,
      tags: List[String],
      dimensions: Dimensions,
      stock: Map[String, Int]
  )

  given dimensionsFormat: OFormat[Dimensions] = Json.format[Dimensions]
  given catalogItemFormat: OFormat[CatalogItem] = Json.format[CatalogItem]

  private val Password: String = "catalog-secret"
  private val ContainerName: String = "real-use-play-json-catalog-" + UUID.randomUUID()
  private var port: Int = 0

  private def jdbcUrl: String = s"jdbc:postgresql://127.0.0.1:$port/postgres"

  private def startPostgres(): Unit = {
    port = Using.resource(new ServerSocket(0))(_.getLocalPort)
    docker(
      "run", "--rm", "-d", "--name", ContainerName,
      "-e", s"POSTGRES_PASSWORD=$Password",
      "-p", s"127.0.0.1:$port:5432",
      "postgres:18-alpine"
    )
    awaitConnection(TimeUnit.SECONDS.toMillis(120))
  }

  private def stopPostgres(): Unit = docker("rm", "-f", ContainerName)

  private def docker(args: String*): Unit = {
    val process: Process = new ProcessBuilder(("docker" +: args)*).inheritIO().start()
    if (!process.waitFor(5, TimeUnit.MINUTES)) {
      process.destroyForcibly()
      throw new IllegalStateException(s"docker ${args.head} timed out")
    }
    if (process.exitValue() != 0) {
      throw new IllegalStateException(s"docker ${args.head} failed with ${process.exitValue()}")
    }
  }

  /** Polls with a bounded deadline until the server accepts connections. */
  private def awaitConnection(timeoutMillis: Long): Unit = {
    val deadline: Long = System.currentTimeMillis() + timeoutMillis
    var lastFailure: Exception = null
    while (System.currentTimeMillis() < deadline) {
      try {
        Using.resource(openConnection())(connection => execute(connection, "SELECT 1"))
        return
      } catch {
        case e: Exception =>
          lastFailure = e
          Thread.sleep(250)
      }
    }
    throw new IllegalStateException("PostgreSQL did not become reachable", lastFailure)
  }

  private def openConnection(): Connection = DriverManager.getConnection(jdbcUrl, "postgres", Password)

  private def execute(connection: Connection, sql: String): Unit =
    Using.resource(connection.createStatement())(_.execute(sql))

  /** Binds the serialized document as `jsonb`, letting PostgreSQL validate and normalize it. */
  private def save(connection: Connection, item: CatalogItem): Unit =
    Using.resource(connection.prepareStatement("INSERT INTO catalog_items (sku, doc) VALUES (?, ?::jsonb)")) {
      statement =>
        statement.setString(1, item.sku)
        statement.setString(2, Json.stringify(Json.toJson(item)))
        statement.executeUpdate()
    }

  private def replace(connection: Connection, sku: String, document: JsObject): Unit =
    Using.resource(connection.prepareStatement("UPDATE catalog_items SET doc = ?::jsonb WHERE sku = ?")) {
      statement =>
        statement.setString(1, Json.stringify(document))
        statement.setString(2, sku)
        assertEquals(1, statement.executeUpdate())
    }

  private def load(connection: Connection, sku: String): Option[JsObject] =
    Using.resource(connection.prepareStatement("SELECT doc::text FROM catalog_items WHERE sku = ?")) {
      statement =>
        statement.setString(1, sku)
        readDocuments(statement).headOption.map(_.as[JsObject])
    }

  /** Finds items whose document contains `criteria`, using the `jsonb` containment operator. */
  private def findContaining(connection: Connection, criteria: JsObject): List[CatalogItem] =
    Using.resource(
      connection.prepareStatement("SELECT doc::text FROM catalog_items WHERE doc @> ?::jsonb ORDER BY sku")
    ) { statement =>
      statement.setString(1, Json.stringify(criteria))
      readDocuments(statement).map(_.as[CatalogItem])
    }

  private def readDocuments(statement: PreparedStatement): List[JsValue] =
    Using.resource(statement.executeQuery()) { resultSet =>
      collect(resultSet).map(Json.parse)
    }

  private def queryStrings(connection: Connection, sql: String): List[String] =
    Using.resource(connection.createStatement()) { statement =>
      Using.resource(statement.executeQuery(sql))(collect)
    }

  private def collect(resultSet: ResultSet): List[String] = {
    val values: ListBuffer[String] = ListBuffer.empty
    while (resultSet.next()) {
      values += resultSet.getString(1)
    }
    values.toList
  }
}
