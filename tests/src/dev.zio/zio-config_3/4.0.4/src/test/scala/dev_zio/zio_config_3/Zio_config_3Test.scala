/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_config_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import zio.Chunk
import zio.Config
import zio.ConfigProvider
import zio.Runtime
import zio.Unsafe
import zio.ZIO
import zio.config.*

import java.util.UUID
import scala.jdk.CollectionConverters.*

class Zio_config_3Test {
  import Zio_config_3Test.*

  @Test
  def readsStructuredConfigurationFromMapProvider(): Unit = {
    val values: Map[String, String] = Map(
      "service.http.host"   -> "127.0.0.1",
      "service.http.port"   -> "8080",
      "service.http.secure" -> "true"
    )
    val provider: ConfigProvider = ConfigProvider.fromMap(values)
    val descriptor: Config[ServerConfig] = Config
      .string("host")
      .zipWith(Config.int("port"))((host: String, port: Int) => (host, port))
      .zipWith(Config.boolean("secure")) { case ((host: String, port: Int), secure: Boolean) =>
        ServerConfig(host, port, secure)
      }
      .nested("http")
      .nested("service")

    val config: ServerConfig = unsafeRun(read(descriptor.from(provider)))

    assertThat(config).isEqualTo(ServerConfig("127.0.0.1", 8080, secure = true))
  }

  @Test
  def transformsKeysAndReadsThroughZioConfigProviderEffect(): Unit = {
    val values: Map[String, String] = Map(
      "SERVER_HOST" -> "api.example.test",
      "SERVER_PORT" -> "443",
      "APP_TOKEN"   -> "secret-token"
    )
    val provider: ConfigProvider = ConfigProvider.fromMap(values)
    val descriptor: Config[(String, Int, String)] = Config
      .string("serverHost")
      .zipWith(Config.int("serverPort"))((host: String, port: Int) => (host, port))
      .zipWith(Config.string("token").mapKey(addPrefixToKey("app"))) { case ((host: String, port: Int), token: String) =>
        (host, port, token)
      }
      .toSnakeCase
      .toUpperCase

    val config: (String, Int, String) = unsafeRun(read(descriptor.from(ZIO.succeed(provider))))

    assertThat(config).isEqualTo(("api.example.test", 443, "secret-token"))
  }

  @Test
  def parsesAdditionalPrimitiveDescriptorsAndConstants(): Unit = {
    val id: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    val values: Map[String, String] = Map(
      "limits.byte" -> "12",
      "limits.short" -> "32000",
      "limits.id" -> id.toString,
      "limits.mode" -> "production"
    )
    val provider: ConfigProvider = ConfigProvider.fromMap(values)
    val descriptor: Config[(Byte, Short, UUID, String)] = Config
      .byte
      .nested("byte")
      .zipWith(Config.short.nested("short"))((byteValue: Byte, shortValue: Short) => (byteValue, shortValue))
      .zipWith(Config.uuid.nested("id")) { case ((byteValue: Byte, shortValue: Short), uuid: UUID) =>
        (byteValue, shortValue, uuid)
      }
      .zipWith(Config.constant("production").nested("mode")) {
        case ((byteValue: Byte, shortValue: Short, uuid: UUID), mode: String) =>
          (byteValue, shortValue, uuid, mode)
      }
      .nested("limits")

    val parsed: (Byte, Short, UUID, String) = unsafeRun(read(descriptor.from(provider)))

    assertThat(parsed).isEqualTo((12.toByte, 32000.toShort, id, "production"))
  }

  @Test
  def convertsTupleConfigurationToCaseClass(): Unit = {
    val values: Map[String, String] = Map(
      "database.url"      -> "jdbc:postgresql://db.example.test/app",
      "database.poolSize" -> "16",
      "database.tls"      -> "true"
    )
    val provider: ConfigProvider = ConfigProvider.fromMap(values)
    val descriptor: Config[DatabaseConfig] = Config
      .string("url")
      .zipWith(Config.int("poolSize"))((url: String, poolSize: Int) => (url, poolSize))
      .zipWith(Config.boolean("tls")) { case ((url: String, poolSize: Int), tls: Boolean) =>
        (url, poolSize, tls)
      }
      .to[DatabaseConfig]
      .nested("database")

    val config: DatabaseConfig = unsafeRun(read(descriptor.from(provider)))

    assertThat(config).isEqualTo(DatabaseConfig("jdbc:postgresql://db.example.test/app", 16, tls = true))
  }

  @Test
  def collectsMultipleDescriptorsIntoOrderedList(): Unit = {
    val values: Map[String, String] = Map(
      "cluster.primary" -> "alpha",
      "cluster.secondary" -> "beta",
      "cluster.tertiary" -> "gamma"
    )
    val provider: ConfigProvider = ConfigProvider.fromMap(values)
    val descriptor: Config[List[String]] = Config
      .collectAll(
        Config.string("primary"),
        Config.string("secondary"),
        Config.string("tertiary")
      )
      .nested("cluster")

    val parsed: List[String] = unsafeRun(read(descriptor.from(provider)))

    assertThat(parsed).isEqualTo(List("alpha", "beta", "gamma"))
  }

  @Test
  def reportsPrettyErrorsForInvalidConfiguration(): Unit = {
    val provider: ConfigProvider = ConfigProvider.fromMap(Map("http.port" -> "not-a-number"))
    val descriptor: Config[Int] = Config.int("port").nested("http")

    val failure: Either[Config.Error, Int] = unsafeRun(read(descriptor.from(provider)).either)

    assertThat(failure.isLeft).isTrue
    val prettyError: String = failure.swap.toOption.get.prettyPrint()
    assertThat(prettyError).contains("ReadError:")
    assertThat(prettyError).contains("FormatError")
    assertThat(prettyError).contains("http.port")
    assertThat(prettyError).contains("not-a-number")
  }

  @Test
  def supportsFallbacksKebabCaseAndEitherResults(): Unit = {
    val provider: ConfigProvider = ConfigProvider.fromMap(Map("backup-port" -> "9090"))
    val descriptor: Config[Either[String, Int]] = Config
      .string("primaryHost")
      .orElseEither(Config.int("backupPort"))
      .toKebabCase

    val parsed: Either[String, Int] = unsafeRun(read(descriptor.from(provider)))

    assertThat(parsed).isEqualTo(Right(9090))
    assertThat(toKebabCase("serverHTTPPort")).isEqualTo("server-httpport")
  }

  @Test
  def generatesDocumentationTablesAndMarkdown(): Unit = {
    val descriptor: Config[(String, Int)] = (Config.string("host") ?? "Public host name")
      .zipWith(Config.int("port") ?? "Listening port")((host: String, port: Int) => (host, port))
      .nested("http")
      .nested("service")
    val table: Table = generateDocs(descriptor).toTable

    val markdown: String = table.toGithubFlavouredMarkdown
    val confluence: String = table.toConfluenceMarkdown(Some("https://docs.example.test/config"))

    assertThat(table.rows.size).isGreaterThanOrEqualTo(1)
    assertThat(markdown).contains("## Configuration Details")
    assertThat(markdown).contains("host")
    assertThat(markdown).contains("primitive")
    assertThat(markdown).contains("Public host name")
    assertThat(confluence).contains("https://docs.example.test/config")
  }

  @Test
  def rendersManualTablesWithCustomFormatsAndSources(): Unit = {
    val table: Table = Table
      .TableRow(
        paths = List(Table.FieldName.Key("workers")),
        format = Some(Table.Format.List),
        description = Nil,
        nested = None,
        sources = Set("env", "props")
      )
      .asTable
      .withFormat(Table.Format.Map)
    val markdown: String = table.toGithubFlavouredMarkdown

    assertThat(Table.Format.Map.asString).isEqualTo("map")
    assertThat(Table.Link.githubLink("workers", "#workers").value).isEqualTo("[workers](#workers)")
    assertThat(markdown).contains("workers")
    assertThat(markdown).contains("map")
    assertThat(markdown).contains("env, props")
  }

  @Test
  def handlesIndexedPathsAndPathInterpolator(): Unit = {
    val parsedPath: IndexedFlat.ConfigPath = IndexedFlat.ConfigPath.fromPath(Chunk("servers[10]", "host"))
    val plainPath: Chunk[String] = IndexedFlat.ConfigPath.toPath(parsedPath)
    val interpolatedPath: Chunk[String] = path"database.primary.host"

    assertThat(parsedPath.toList.map(_.value).asJava).containsExactly("servers", "[10]", "host")
    assertThat(plainPath.toList.asJava).containsExactly("servers[10]", "host")
    assertThat(interpolatedPath.toList.asJava).containsExactly("database", "primary", "host")
    assertThat(IndexedFlat.KeyComponent.KeyName("server").mapName(_.toUpperCase).value).isEqualTo("SERVER")
    assertThat(IndexedFlat.KeyComponent.Index(2).mapName(_ => "ignored").value).isEqualTo("[2]")
  }
}

object Zio_config_3Test {
  private final case class ServerConfig(host: String, port: Int, secure: Boolean)

  private final case class DatabaseConfig(url: String, poolSize: Int, tls: Boolean)

  private def unsafeRun[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
