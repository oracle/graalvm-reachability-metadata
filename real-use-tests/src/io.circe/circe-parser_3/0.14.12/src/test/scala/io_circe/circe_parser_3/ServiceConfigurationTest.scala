/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import io.circe.Decoder
import io.circe.parser.decode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises typed JSON configuration decoding for nested service settings and application defaults. */
@Timeout(60)
class ServiceConfigurationTest {
  private final case class Endpoint(host: String, port: Int, tls: Boolean)

  private final case class ServiceConfig(
      name: String,
      endpoint: Endpoint,
      allowedOrigins: List[String],
      credentials: Map[String, String],
      requestTimeoutSeconds: Int
  )

  private given Decoder[Endpoint] =
    Decoder.forProduct3("host", "port", "tls")(Endpoint.apply)

  private given Decoder[ServiceConfig] = Decoder.instance { cursor =>
    for {
      name <- cursor.get[String]("name")
      endpoint <- cursor.get[Endpoint]("endpoint")
      allowedOrigins <- cursor.get[List[String]]("allowedOrigins")
      credentials <- cursor.get[Map[String, String]]("credentials")
      timeout <- cursor.getOrElse[Int]("requestTimeoutSeconds")(30)
    } yield ServiceConfig(name, endpoint, allowedOrigins, credentials, timeout)
  }

  @Test
  def loadsServiceSettingsWithAnExplicitOrDefaultRequestTimeout(): Unit = {
    val productionJson: String =
      """{
        |  "name": "order-gateway",
        |  "endpoint": {"host": "orders.example.test", "port": 8443, "tls": true},
        |  "allowedOrigins": ["https://shop.example.test", "https://admin.example.test"],
        |  "credentials": {"username": "gateway", "token": "example-token"},
        |  "requestTimeoutSeconds": 45
        |}""".stripMargin

    val production: ServiceConfig = ServiceConfig(
      "order-gateway",
      Endpoint("orders.example.test", 8443, true),
      List("https://shop.example.test", "https://admin.example.test"),
      Map("username" -> "gateway", "token" -> "example-token"),
      45
    )

    assertEquals(Right(production), decode[ServiceConfig](productionJson))

    val developmentJson: String =
      """{
        |  "name": "order-gateway-dev",
        |  "endpoint": {"host": "localhost", "port": 8080, "tls": false},
        |  "allowedOrigins": [],
        |  "credentials": {}
        |}""".stripMargin

    val development: ServiceConfig = ServiceConfig(
      "order-gateway-dev",
      Endpoint("localhost", 8080, false),
      List.empty,
      Map.empty,
      30
    )

    assertEquals(Right(development), decode[ServiceConfig](developmentJson))
  }
}
