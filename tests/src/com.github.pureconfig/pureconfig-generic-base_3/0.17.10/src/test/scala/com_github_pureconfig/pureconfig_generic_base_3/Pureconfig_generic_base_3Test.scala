/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_pureconfig.pureconfig_generic_base_3

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import pureconfig.CamelCase
import pureconfig.ConfigCursor
import pureconfig.ConfigFieldMapping
import pureconfig.ConfigObjectCursor
import pureconfig.KebabCase
import pureconfig.error.ConfigReaderFailures
import pureconfig.error.UnknownKey
import pureconfig.generic.CoproductHint
import pureconfig.generic.FieldCoproductHint
import pureconfig.generic.FirstSuccessCoproductHint
import pureconfig.generic.ProductHint
import pureconfig.generic.error.CoproductHintException

class Pureconfig_generic_base_3Test {
  @Test
  def productHintUsesDefaultKebabCaseMappingAndDefaults(): Unit = {
    val cursor: ConfigObjectCursor = objectCursor("""
      connection-timeout = 25
      mode = "active"
      ignored-by-default = true
      """)
    val hint: ProductHint[ServerSettings] = summon[ProductHint[ServerSettings]]

    hint.from(cursor, "connectionTimeout") match {
      case ProductHint.UseOrDefault(fieldCursor, field) =>
        assertThat(field).isEqualTo("connection-timeout")
        assertThat(right(fieldCursor.asInt)).isEqualTo(25)
      case other => fail(s"Expected ProductHint.UseOrDefault, got $other")
    }

    assertThat(hint.bottom(cursor, Set("connection-timeout", "mode"))).isEqualTo(None)
    assertThat(hint.to(Some(configValue("passive")), "connectionMode")).isEqualTo(
      Some("connection-mode" -> configValue("passive"))
    )
    assertThat(hint.to(None, "connectionMode")).isEqualTo(None)
  }

  @Test
  def productHintDefersMissingFieldsToDefaultArgumentHandling(): Unit = {
    val cursor: ConfigObjectCursor = objectCursor("""
      host-name = "service.local"
      """)
    val hint: ProductHint[ServerSettings] = summon[ProductHint[ServerSettings]]

    hint.from(cursor, "connectionTimeout") match {
      case ProductHint.UseOrDefault(fieldCursor, field) =>
        assertThat(field).isEqualTo("connection-timeout")
        assertThat(left(fieldCursor.asInt).prettyPrint()).contains("connection-timeout")
      case other => fail(s"Expected ProductHint.UseOrDefault, got $other")
    }
  }

  @Test
  def strictProductHintReportsUnknownKeysAndDoesNotUseDefaults(): Unit = {
    val cursor: ConfigObjectCursor = objectCursor("""
      host-name = "localhost"
      port = 8080
      stale-key = "not consumed"
      """)
    val hint: ProductHint[ServerSettings] = ProductHint[ServerSettings](
      fieldMapping = ConfigFieldMapping(CamelCase, KebabCase),
      useDefaultArgs = false,
      allowUnknownKeys = false
    )

    hint.from(cursor, "hostName") match {
      case ProductHint.Use(fieldCursor, field) =>
        assertThat(field).isEqualTo("host-name")
        assertThat(right(fieldCursor.asString)).isEqualTo("localhost")
      case other => fail(s"Expected ProductHint.Use, got $other")
    }

    val failures: ConfigReaderFailures = hint
      .bottom(cursor, Set("host-name", "port"))
      .getOrElse(fail("Expected unknown key failures"))
    assertThat(failures.toList.size).isEqualTo(1)
    assertThat(failures.prettyPrint()).contains("Unknown key")
  }

  @Test
  def productHintSupportsExplicitFieldOverridesForReadingAndWriting(): Unit = {
    val cursor: ConfigObjectCursor = objectCursor("""
      external-host = "example.org"
      api-port = 9443
      """)
    val mapping: ConfigFieldMapping = ConfigFieldMapping(CamelCase, KebabCase).withOverrides(
      "hostName" -> "external-host",
      "port" -> "api-port"
    )
    val hint: ProductHint[ServerSettings] = ProductHint[ServerSettings](fieldMapping = mapping)

    assertThat(right(hint.from(cursor, "hostName").cursor.asString)).isEqualTo("example.org")
    assertThat(right(hint.from(cursor, "port").cursor.asInt)).isEqualTo(9443)
    assertThat(hint.to(Some(configValue(Integer.valueOf(9443))), "port")).isEqualTo(
      Some("api-port" -> configValue(Integer.valueOf(9443)))
    )
  }

  @Test
  def fieldCoproductHintReadsDiscriminatorAndRemovesItFromSelectedCursor(): Unit = {
    val cursor: ConfigObjectCursor = objectCursor("""
      type = "http-server"
      port = 8080
      enabled = true
      """)
    val hint: CoproductHint[Transport] = summon[CoproductHint[Transport]]

    right(hint.from(cursor, Seq("HttpServer", "UnixSocket"))) match {
      case CoproductHint.Use(selectedCursor, option) =>
        assertThat(option).isEqualTo("HttpServer")
        val selectedObject: ConfigObjectCursor = right(selectedCursor.asObjectCursor)
        assertThat(selectedObject.map.contains("type")).isFalse
        assertThat(right(selectedObject.atKey("port").flatMap(_.asInt))).isEqualTo(8080)
      case other => fail(s"Expected CoproductHint.Use, got $other")
    }
  }

  @Test
  def fieldCoproductHintReportsUnexpectedDiscriminatorValues(): Unit = {
    val cursor: ConfigObjectCursor = objectCursor("""
      type = "datagram-server"
      port = 9999
      """)
    val hint: CoproductHint[Transport] = new FieldCoproductHint[Transport]("type")

    val failures: ConfigReaderFailures = left(hint.from(cursor, Seq("HttpServer", "UnixSocket")))
    assertThat(failures.prettyPrint()).contains("Unexpected value", "datagram-server")
  }

  @Test
  def fieldCoproductHintWritesDiscriminatorAndRejectsInvalidTargetValues(): Unit = {
    val hint: CoproductHint[Transport] = new FieldCoproductHint[Transport]("type")
    val encoded: ConfigObject = hint.to(configObject("port = 8080"), "HttpServer") match {
      case obj: ConfigObject => obj
      case other => fail(s"Expected ConfigObject, got $other")
    }

    assertThat(encoded.toConfig.getString("type")).isEqualTo("http-server")
    assertThat(encoded.toConfig.getInt("port")).isEqualTo(8080)
    assertThatThrownBy(() => hint.to(configObject("type = already-present"), "HttpServer"))
      .isInstanceOf(classOf[CoproductHintException])
    assertThatThrownBy(() => hint.to(configValue("not-an-object"), "HttpServer"))
      .isInstanceOf(classOf[CoproductHintException])
  }

  @Test
  def fieldCoproductHintCanCustomizeDiscriminatorValues(): Unit = {
    val cursor: ConfigObjectCursor = objectCursor("""
      kind = "UNIXSOCKET"
      path = "/tmp/service.sock"
      """)
    val hint: CoproductHint[Transport] = new UpperCaseCoproductHint[Transport]("kind")

    right(hint.from(cursor, Seq("HttpServer", "UnixSocket"))) match {
      case CoproductHint.Use(selectedCursor, option) =>
        assertThat(option).isEqualTo("UnixSocket")
        assertThat(right(selectedCursor.asObjectCursor).map.contains("kind")).isFalse
      case other => fail(s"Expected CoproductHint.Use, got $other")
    }

    val encoded: ConfigObject = hint.to(configObject("path = \"/tmp/service.sock\""), "UnixSocket") match {
      case obj: ConfigObject => obj
      case other => fail(s"Expected ConfigObject, got $other")
    }
    assertThat(encoded.toConfig.getString("kind")).isEqualTo("UNIXSOCKET")
  }

  @Test
  def firstSuccessCoproductHintAttemptsAllOptionsAndCombinesFailures(): Unit = {
    val cursor: ConfigObjectCursor = objectCursor("""
      host = "localhost"
      port = 8080
      """)
    val value: ConfigValue = cursor.objValue
    val hint: CoproductHint[Transport] = new FirstSuccessCoproductHint[Transport]

    right(hint.from(cursor, Seq("HttpServer", "UnixSocket"))) match {
      case CoproductHint.Attempt(attemptCursor, options, combineFailures) =>
        assertThat(attemptCursor).isSameAs(cursor)
        assertThat(options).isEqualTo(Seq("HttpServer", "UnixSocket"))

        val optionFailures: Seq[(String, ConfigReaderFailures)] = Seq(
          "HttpServer" -> ConfigReaderFailures(cursor.failureFor(UnknownKey("unexpected-http-key"))),
          "UnixSocket" -> ConfigReaderFailures(cursor.failureFor(UnknownKey("unexpected-unix-key")))
        )
        val combined: ConfigReaderFailures = combineFailures(optionFailures)
        assertThat(combined.prettyPrint()).contains(
          "No valid coproduct option found",
          "HttpServer",
          "Unknown key",
          "UnixSocket"
        )
      case other => fail(s"Expected CoproductHint.Attempt, got $other")
    }

    assertThat(hint.to(value, "HttpServer")).isSameAs(value)
  }

  private def objectCursor(config: String): ConfigObjectCursor =
    ConfigObjectCursor(configObject(config), Nil)

  private def configObject(config: String): ConfigObject =
    ConfigFactory.parseString(config).root

  private def configValue(value: AnyRef): ConfigValue =
    ConfigValueFactory.fromAnyRef(value)

  private def right[A](result: Either[ConfigReaderFailures, A]): A =
    result match {
      case Right(value) => value
      case Left(failures) => fail(s"Expected Right, got ${failures.prettyPrint()}")
    }

  private def left[A](result: Either[ConfigReaderFailures, A]): ConfigReaderFailures =
    result match {
      case Right(value) => fail(s"Expected Left, got $value")
      case Left(failures) => failures
    }

  private def fail(message: String): Nothing =
    throw new AssertionError(message)
}

private final case class ServerSettings(hostName: String = "localhost", connectionTimeout: Int = 5, port: Int = 80)

private sealed trait Transport
private final case class HttpServer(port: Int) extends Transport
private final case class UnixSocket(path: String) extends Transport

private final class UpperCaseCoproductHint[A](key: String) extends FieldCoproductHint[A](key) {
  override protected def fieldValue(name: String): String = name.toUpperCase(java.util.Locale.ROOT)
}
