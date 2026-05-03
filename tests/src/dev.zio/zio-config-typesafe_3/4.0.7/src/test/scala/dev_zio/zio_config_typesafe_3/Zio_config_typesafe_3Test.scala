/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_config_typesafe_3

import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import zio.Config
import zio.ConfigProvider
import zio.Runtime
import zio.Unsafe
import zio.ZIO
import zio.config.*
import zio.config.typesafe.*
import zio.config.typesafe.TypesafeConfigProvider

import java.nio.file.Files
import java.nio.file.Path

class Zio_config_typesafe_3Test {
  import Zio_config_typesafe_3Test.*

  @Test
  def readsNestedPrimitivesFromHoconString(): Unit = {
    val hocon: String = """
      service {
        host = "api.example.test"
        port = 8443
        secure = true
      }
      limits {
        request-timeout = 30 seconds
        weight = 2.5
      }
      """
    val provider: ConfigProvider = TypesafeConfigProvider.fromHoconString(hocon)
    val descriptor: Config[ServiceConfig] = Config
      .string("host")
      .zipWith(Config.int("port"))((host: String, port: Int) => (host, port))
      .zipWith(Config.boolean("secure")) { case ((host: String, port: Int), secure: Boolean) =>
        ServiceConfig(host, port, secure)
      }
      .nested("service")

    val parsed: ServiceConfig = unsafeRun(read(descriptor.from(provider)))
    val timeout: java.time.Duration = unsafeRun(read(Config.duration("request-timeout").nested("limits").from(provider)))
    val weight: Double = unsafeRun(read(Config.double("weight").nested("limits").from(provider)))

    assertThat(parsed).isEqualTo(ServiceConfig("api.example.test", 8443, secure = true))
    assertThat(timeout).isEqualTo(java.time.Duration.ofSeconds(30))
    assertThat(weight).isEqualTo(2.5d)
  }

  @Test
  def readsArraysOfObjectsAndPrimitiveListsFromHoconString(): Unit = {
    val hocon: String = """
      servers = [
        { host = "alpha.example.test", port = 8080, enabled = true },
        { host = "beta.example.test", port = 8081, enabled = false }
      ]
      tags = ["blue", "green", "canary"]
      """
    val provider: ConfigProvider = ConfigProvider.fromHoconString(hocon)
    val serverDescriptor: Config[ServerEntry] = Config
      .string("host")
      .zipWith(Config.int("port"))((host: String, port: Int) => (host, port))
      .zipWith(Config.boolean("enabled")) { case ((host: String, port: Int), enabled: Boolean) =>
        ServerEntry(host, port, enabled)
      }
    val descriptor: Config[(List[ServerEntry], List[String])] = Config
      .listOf("servers", serverDescriptor)
      .zipWith(Config.listOf("tags", Config.string))((servers: List[ServerEntry], tags: List[String]) => (servers, tags))

    val parsed: (List[ServerEntry], List[String]) = unsafeRun(read(descriptor.from(provider)))

    assertThat(parsed._1).isEqualTo(
      List(
        ServerEntry("alpha.example.test", 8080, enabled = true),
        ServerEntry("beta.example.test", 8081, enabled = false)
      )
    )
    assertThat(parsed._2).isEqualTo(List("blue", "green", "canary"))
  }

  @Test
  def supportsCommaSeparatedValuesWhenEnabled(): Unit = {
    val hocon: String = """
      app {
        enabled-modules = "users,billing,search"
        literal-modules = "users,billing,search"
      }
      """
    val provider: ConfigProvider = TypesafeConfigProvider.fromHoconString(hocon, enableCommaSeparatedValueAsList = true)
    val modulesDescriptor: Config[List[String]] = Config.listOf("enabled-modules", Config.string).nested("app")
    val literalDescriptor: Config[String] = Config.string("literal-modules").nested("app")

    val modules: List[String] = unsafeRun(read(modulesDescriptor.from(provider)))
    val literal: String = unsafeRun(read(literalDescriptor.from(provider)))

    assertThat(modules).isEqualTo(List("users", "billing", "search"))
    assertThat(literal).isEqualTo("users,billing,search")
  }

  @Test
  def readsResolvedTypesafeConfigAndPreservesQuotedKeyComponents(): Unit = {
    val rawConfig: com.typesafe.config.Config = ConfigFactory.parseString("""
      defaults {
        port = 9000
      }
      service {
        port = ${defaults.port}
        "owner.name" = "platform-team"
      }
      """).resolve()
    val provider: ConfigProvider = TypesafeConfigProvider.fromTypesafeConfig(rawConfig)
    val descriptor: Config[(Int, String)] = Config
      .int("port")
      .zipWith(Config.string("owner.name"))((port: Int, owner: String) => (port, owner))
      .nested("service")

    val parsed: (Int, String) = unsafeRun(read(descriptor.from(provider)))

    assertThat(parsed).isEqualTo((9000, "platform-team"))
  }

  @Test
  def readsObjectTablesWithDynamicKeysFromHocon(): Unit = {
    val hocon: String = """
      tenants {
        alpha {
          url = "jdbc:postgresql://alpha.example.test/app"
          pool-size = 4
        }
        beta {
          url = "jdbc:postgresql://beta.example.test/app"
          pool-size = 8
        }
      }
      """
    val provider: ConfigProvider = TypesafeConfigProvider.fromHoconString(hocon)
    val tenantDescriptor: Config[DatabaseConfig] = Config
      .string("url")
      .zipWith(Config.int("pool-size"))((url: String, poolSize: Int) => DatabaseConfig(url, poolSize))
    val descriptor: Config[Map[String, DatabaseConfig]] = Config.table("tenants", tenantDescriptor)

    val parsed: Map[String, DatabaseConfig] = unsafeRun(read(descriptor.from(provider)))

    assertThat(parsed).isEqualTo(
      Map(
        "alpha" -> DatabaseConfig("jdbc:postgresql://alpha.example.test/app", 4),
        "beta" -> DatabaseConfig("jdbc:postgresql://beta.example.test/app", 8)
      )
    )
  }

  @Test
  def decodesEmptyListsFromHocon(): Unit = {
    val hocon: String = """
      deployment {
        release-channels = []
        ports = []
        replicas = 3
      }
      """
    val provider: ConfigProvider = TypesafeConfigProvider.fromHoconString(hocon)
    val descriptor: Config[(List[String], List[Int], Int)] = Config
      .listOf("release-channels", Config.string)
      .zipWith(Config.listOf("ports", Config.int))((channels: List[String], ports: List[Int]) => (channels, ports))
      .zipWith(Config.int("replicas")) { case ((channels: List[String], ports: List[Int]), replicas: Int) =>
        (channels, ports, replicas)
      }
      .nested("deployment")

    val parsed: (List[String], List[Int], Int) = unsafeRun(read(descriptor.from(provider)))

    assertThat(parsed).isEqualTo((List.empty[String], List.empty[Int], 3))
  }

  @Test
  def readsConfigurationFromFileAndFilePathProviders(): Unit = {
    val configFile: Path = Files.createTempFile("zio-config-typesafe", ".conf")
    try {
      Files.writeString(
        configFile,
        """
        database {
          url = "jdbc:postgresql://db.example.test/app"
          pool-size = 16
        }
        feature-flags = ["checkout", "invoicing"]
        """
      )
      val descriptor: Config[(DatabaseConfig, List[String])] = Config
        .string("url")
        .zipWith(Config.int("pool-size"))((url: String, poolSize: Int) => DatabaseConfig(url, poolSize))
        .nested("database")
        .zipWith(Config.listOf("feature-flags", Config.string))((database: DatabaseConfig, flags: List[String]) =>
          (database, flags)
        )

      val fromFile: (DatabaseConfig, List[String]) =
        unsafeRun(read(descriptor.from(TypesafeConfigProvider.fromHoconFile(configFile.toFile))))
      val fromPath: (DatabaseConfig, List[String]) =
        unsafeRun(read(descriptor.from(TypesafeConfigProvider.fromHoconFilePath(configFile.toString))))

      assertThat(fromFile).isEqualTo((DatabaseConfig("jdbc:postgresql://db.example.test/app", 16), List("checkout", "invoicing")))
      assertThat(fromPath).isEqualTo(fromFile)
    } finally {
      Files.deleteIfExists(configFile)
    }
  }

  @Test
  def createsProvidersThroughZioConstructors(): Unit = {
    val configFile: Path = Files.createTempFile("zio-config-typesafe-zio", ".conf")
    try {
      Files.writeString(configFile, "service.name = \"zio-file\"")
      val fromString: ConfigProvider = unsafeRun(ConfigProvider.fromHoconStringZIO("service.name = \"zio-string\""))
      val fromTypesafe: ConfigProvider = unsafeRun(
        ConfigProvider.fromTypesafeConfigZIO(ConfigFactory.parseString("service.name = \"zio-typesafe\""))
      )
      val fromFile: ConfigProvider = unsafeRun(ConfigProvider.fromHoconFileZIO(configFile.toFile))
      val fromFilePath: ConfigProvider = unsafeRun(ConfigProvider.fromHoconFilePathZIO(configFile.toString))
      val descriptor: Config[String] = Config.string("name").nested("service")

      val values: List[String] = List(fromString, fromTypesafe, fromFile, fromFilePath).map { (provider: ConfigProvider) =>
        unsafeRun(read(descriptor.from(provider)))
      }

      assertThat(values).isEqualTo(List("zio-string", "zio-typesafe", "zio-file", "zio-file"))
    } finally {
      Files.deleteIfExists(configFile)
    }
  }

  @Test
  def exposesClasspathProviderWithoutRequiringApplicationConfig(): Unit = {
    val provider: ConfigProvider = ConfigProvider.fromResourcePath()
    val optionalDescriptor: Config[Option[String]] = Config.string("missing-zio-config-typesafe-test-value").optional

    val parsed: Option[String] = unsafeRun(read(optionalDescriptor.from(provider)))

    assertThat(parsed).isEqualTo(None)
  }

  @Test
  def exposesClasspathProviderThroughZioConstructor(): Unit = {
    val provider: ConfigProvider = unsafeRun(ConfigProvider.fromResourcePathZIO())
    val optionalDescriptor: Config[Option[Int]] = Config.int("missing-zio-config-typesafe-test-port").optional

    val parsed: Option[Int] = unsafeRun(read(optionalDescriptor.from(provider)))

    assertThat(parsed).isEqualTo(None)
  }

  @Test
  def reportsReadableErrorsForMalformedValues(): Unit = {
    val provider: ConfigProvider = TypesafeConfigProvider.fromHoconString("server.port = \"not-a-number\"")
    val descriptor: Config[Int] = Config.int("port").nested("server")

    val result: Either[Config.Error, Int] = unsafeRun(read(descriptor.from(provider)).either)

    assertThat(result.isLeft).isTrue
    val error: String = result.swap.toOption.get.prettyPrint()
    assertThat(error).contains("ReadError:")
    assertThat(error).contains("FormatError")
    assertThat(error).contains("server.port")
    assertThat(error).contains("not-a-number")
  }
}

object Zio_config_typesafe_3Test {
  private final case class ServiceConfig(host: String, port: Int, secure: Boolean)

  private final case class ServerEntry(host: String, port: Int, enabled: Boolean)

  private final case class DatabaseConfig(url: String, poolSize: Int)

  private def unsafeRun[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
