/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_server_3

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import org.http4s.AuthedRequest
import org.http4s.BasicCredentials
import org.http4s.ContextRoutes
import org.http4s.EntityDecoder
import org.http4s.Header
import org.http4s.HttpApp
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.UrlForm
import org.http4s.headers.Authorization
import org.http4s.implicits.*
import org.http4s.server.ContextMiddleware
import org.http4s.server.Router
import org.http4s.server.middleware.AutoSlash
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.CORSConfig
import org.http4s.server.middleware.DefaultHead
import org.http4s.server.middleware.EntityLimiter
import org.http4s.server.middleware.HSTS
import org.http4s.server.middleware.HeaderEcho
import org.http4s.server.middleware.RequestId
import org.http4s.server.middleware.ResponseTiming
import org.http4s.server.middleware.StaticHeaders
import org.http4s.server.middleware.TranslateUri
import org.http4s.server.middleware.UrlFormLifter
import org.http4s.server.middleware.VirtualHost
import org.http4s.server.middleware.authentication.BasicAuth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci.CIString
import org.typelevel.ci.*

import java.util.UUID
import scala.concurrent.duration.*

class Http4s_server_3Test {
  import Http4s_server_3Test.*

  @Test
  def routerSelectsLongestStaticPrefixAndFallsBackToDefaultRoutes(): Unit = {
    val routed: HttpRoutes[IO] = Router.define[IO](
      "/api" -> pathInfoRoute("api"),
      "/api/admin" -> pathInfoRoute("admin"),
    )(pathInfoRoute("default"))

    val adminResponse: Response[IO] = runRoutes(routed, Request[IO](Method.GET, uri"/api/admin/users/42"))
    assertEquals(Status.Ok, adminResponse.status)
    assertEquals("admin:/users/42", bodyText(adminResponse))

    val apiResponse: Response[IO] = runRoutes(routed, Request[IO](Method.GET, uri"/api/reports"))
    assertEquals(Status.Ok, apiResponse.status)
    assertEquals("api:/reports", bodyText(apiResponse))

    val defaultResponse: Response[IO] = runRoutes(routed, Request[IO](Method.GET, uri"/health"))
    assertEquals(Status.Ok, defaultResponse.status)
    assertEquals("default:/health", bodyText(defaultResponse))
  }

  @Test
  def contextMiddlewareBuildsAuthedContextFromRequestHeaders(): Unit = {
    val contextMiddleware: ContextMiddleware[IO, String] = ContextMiddleware[IO, String] {
      Kleisli { request =>
        OptionT.fromOption[IO](request.headers.get(ci"X-Tenant").map(_.head.value))
      }
    }
    val contextRoutes: ContextRoutes[String, IO] = Kleisli { contextRequest =>
      OptionT.liftF(
        Response[IO](Status.Ok)
          .withEntity(s"tenant=${contextRequest.context},path=${contextRequest.req.pathInfo.renderString}")
          .pure[IO]
      )
    }
    val routes: HttpRoutes[IO] = contextMiddleware(contextRoutes)

    val authenticated: Response[IO] = runRoutes(
      routes,
      Request[IO](Method.GET, uri"/private/data").putHeaders(Header.Raw(ci"X-Tenant", "acme")),
    )
    assertEquals(Status.Ok, authenticated.status)
    assertEquals("tenant=acme,path=/private/data", bodyText(authenticated))

    val anonymous: Response[IO] = runRoutes(routes, Request[IO](Method.GET, uri"/private/data"))
    assertEquals(Status.NotFound, anonymous.status)
  }

  @Test
  def autoSlashTranslateUriAndDefaultHeadRewriteRequestsWithoutChangingApplicationCode(): Unit = {
    val exactRoutes: HttpRoutes[IO] = Kleisli { request =>
      if (request.method == Method.GET && request.pathInfo.renderString == "/resource")
        OptionT.liftF(Response[IO](Status.Ok).withEntity("resource-body").pure[IO])
      else OptionT.none[IO, Response[IO]]
    }
    val slashAwareRoutes: HttpRoutes[IO] = DefaultHead.httpRoutes(AutoSlash.httpRoutes(exactRoutes))

    val trailingSlash: Response[IO] = runRoutes(slashAwareRoutes, Request[IO](Method.GET, uri"/resource/"))
    assertEquals(Status.Ok, trailingSlash.status)
    assertEquals("resource-body", bodyText(trailingSlash))

    val headResponse: Response[IO] = runRoutes(slashAwareRoutes, Request[IO](Method.HEAD, uri"/resource"))
    assertEquals(Status.Ok, headResponse.status)
    assertEquals("", bodyText(headResponse))

    val translated: HttpRoutes[IO] = TranslateUri("/prefix")(pathInfoRoute("translated"))
    val translatedResponse: Response[IO] = runRoutes(translated, Request[IO](Method.GET, uri"/prefix/nested/value"))
    assertEquals(Status.Ok, translatedResponse.status)
    assertEquals("translated:/nested/value", bodyText(translatedResponse))

    val unmatchedResponse: Response[IO] = runRoutes(translated, Request[IO](Method.GET, uri"/elsewhere/nested/value"))
    assertEquals(Status.NotFound, unmatchedResponse.status)
  }

  @Test
  def requestIdHeaderEchoStaticHeadersHstsAndResponseTimingDecorateResponses(): Unit = {
    val requestId: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    val baseApp: HttpApp[IO] = HttpApp[IO] { request =>
      val requestIdValue: String = request.attributes.lookup(RequestId.requestIdAttrKey).getOrElse("missing")
      Response[IO](Status.Ok).withEntity(s"request-id=$requestIdValue").pure[IO]
    }
    val decorated: HttpApp[IO] = ResponseTiming(
      HSTS.httpApp.unsafeFromDuration(
        StaticHeaders.`no-cache`(
          HeaderEcho.httpApp(name => name == ci"X-Echo")(
            RequestId.httpApp[IO](ci"X-Correlation-ID", IO.pure(requestId))(baseApp)
          )
        ),
        maxAge = 30.seconds,
        includeSubDomains = true,
        preload = true,
      ),
      MILLISECONDS,
      ci"X-Test-Time",
    )

    val generated: Response[IO] = run(
      decorated(Request[IO](Method.GET, uri"/decorated").putHeaders(Header.Raw(ci"X-Echo", "echo-me")))
    )
    assertEquals(Status.Ok, generated.status)
    assertEquals("request-id=123e4567-e89b-12d3-a456-426614174000", bodyText(generated))
    assertEquals(Some("123e4567-e89b-12d3-a456-426614174000"), rawHeader(generated, ci"X-Correlation-ID"))
    assertEquals(Some("echo-me"), rawHeader(generated, ci"X-Echo"))
    assertEquals(Some("no-cache"), rawHeader(generated, ci"Cache-Control"))
    assertEquals(Some("max-age=30; includeSubDomains; preload"), rawHeader(generated, ci"Strict-Transport-Security"))
    assertTrue(rawHeader(generated, ci"X-Test-Time").exists(_.toLong >= 0L))
    assertEquals(Some("123e4567-e89b-12d3-a456-426614174000"), generated.attributes.lookup(RequestId.requestIdAttrKey))

    val propagated: Response[IO] = run(
      decorated(
        Request[IO](Method.GET, uri"/decorated")
          .putHeaders(Header.Raw(ci"X-Correlation-ID", "caller-supplied"))
      )
    )
    assertEquals("request-id=caller-supplied", bodyText(propagated))
    assertEquals(Some("caller-supplied"), rawHeader(propagated, ci"X-Correlation-ID"))
  }

  @Test
  def corsPolicyHandlesActualAndPreflightCorsRequests(): Unit = {
    val app: HttpApp[IO] = HttpApp[IO] { request =>
      Response[IO](Status.Ok)
        .withEntity(s"served-${request.method.name}")
        .putHeaders(Header.Raw(ci"X-Response-Token", "token-value"))
        .pure[IO]
    }
    val corsConfig: CORSConfig = CORSConfig.default
      .withAllowCredentials(false)
      .withAnyMethod(false)
      .withAllowedMethods(Some(Set(Method.GET, Method.POST)))
      .withAllowedHeaders(Some(Set("X-Requested-With")))
      .withExposedHeaders(Some(Set("X-Response-Token")))
      .withMaxAge(20.seconds)
    val corsApp: HttpApp[IO] = CORS(app, corsConfig)

    val actual: Response[IO] = run(
      corsApp(Request[IO](Method.GET, uri"/cors").putHeaders(Header.Raw(ci"Origin", "https://client.example")))
    )
    assertEquals(Status.Ok, actual.status)
    assertEquals("served-GET", bodyText(actual))
    assertEquals(Some("https://client.example"), rawHeader(actual, ci"Access-Control-Allow-Origin"))
    assertTrue(hasCommaSeparatedHeaderValue(actual, ci"Access-Control-Expose-Headers", "X-Response-Token"))
    assertEquals(Some("token-value"), rawHeader(actual, ci"X-Response-Token"))

    val preflight: Response[IO] = run(
      corsApp(
        Request[IO](Method.OPTIONS, uri"/cors").putHeaders(
          Header.Raw(ci"Origin", "https://client.example"),
          Header.Raw(ci"Access-Control-Request-Method", "POST"),
          Header.Raw(ci"Access-Control-Request-Headers", "X-Requested-With"),
        )
      )
    )
    assertEquals(Status.Ok, preflight.status)
    assertEquals("", bodyText(preflight))
    assertEquals(Some("https://client.example"), rawHeader(preflight, ci"Access-Control-Allow-Origin"))
    assertEquals(Set("GET", "POST"), commaSeparatedHeaderValues(preflight, ci"Access-Control-Allow-Methods"))
    assertTrue(hasCommaSeparatedHeaderValue(preflight, ci"Access-Control-Allow-Headers", "X-Requested-With"))
    assertEquals(Some("20"), rawHeader(preflight, ci"Access-Control-Max-Age"))

    val nonCors: Response[IO] = run(corsApp(Request[IO](Method.GET, uri"/cors")))
    assertEquals(Status.Ok, nonCors.status)
    assertEquals("served-GET", bodyText(nonCors))
    assertEquals(None, rawHeader(nonCors, ci"Access-Control-Allow-Origin"))
  }

  @Test
  def entityLimiterAllowsBoundedBodiesAndFailsOversizedBodiesWhenRead(): Unit = {
    val echoLength: HttpApp[IO] = HttpApp[IO] { request =>
      request.as[String].map(body => Response[IO](Status.Ok).withEntity(s"length=${body.length}"))
    }
    val limited: HttpApp[IO] = EntityLimiter.httpApp(echoLength, limit = 5L)

    val accepted: Response[IO] = run(limited(Request[IO](Method.POST, uri"/limited").withEntity("12345")))
    assertEquals(Status.Ok, accepted.status)
    assertEquals("length=5", bodyText(accepted))

    val rejected: Either[Throwable, Response[IO]] = run(
      limited(Request[IO](Method.POST, uri"/limited").withEntity("123456")).attempt
    )
    rejected match {
      case Left(error) => assertTrue(error.isInstanceOf[EntityLimiter.EntityTooLarge])
      case Right(value) => throw new AssertionError(s"expected oversized entity failure, got $value")
    }
  }

  @Test
  def urlFormLifterMergesFormBodiesIntoQueryParametersAndClearsConsumedBody(): Unit = {
    val formApp: HttpApp[IO] = HttpApp[IO] { request =>
      val fieldValues: String = request.multiParams.getOrElse("field", Seq.empty).mkString("|")
      val keepValue: String = request.params.getOrElse("keep", "missing")
      request.body.compile.toVector.map { bytes =>
        val remainingBody: String = new String(bytes.toArray, java.nio.charset.StandardCharsets.UTF_8)
        Response[IO](Status.Ok).withEntity(s"field=$fieldValues,keep=$keepValue,body=$remainingBody")
      }
    }
    val lifted: HttpApp[IO] = UrlFormLifter.httpApp(formApp)
    val request: Request[IO] = Request[IO](Method.POST, uri"/submit?field=query&keep=yes")
      .withEntity(UrlForm("field" -> "body", "field" -> "second"))

    val response: Response[IO] = run(lifted(request))

    assertEquals(Status.Ok, response.status)
    assertEquals("field=query|body|second,keep=yes,body=", bodyText(response))
  }

  @Test
  def virtualHostDispatchesByHostHeaderAndReportsHostErrors(): Unit = {
    val adminApp: HttpApp[IO] = hostApp("admin")
    val wildcardApp: HttpApp[IO] = hostApp("wildcard")
    val regexApp: HttpApp[IO] = hostApp("regex")
    val virtualHostApp: HttpApp[IO] = VirtualHost(
      VirtualHost.exact(adminApp, "admin.example.com", Some(8443)),
      VirtualHost.wildcard(wildcardApp, "*.example.com"),
      VirtualHost.regex(regexApp, "api[0-9]+\\.service\\.test"),
    )

    val exactByUriPort: Response[IO] = run(
      virtualHostApp(
        Request[IO](Method.GET, uri"http://admin.example.com:8443/dashboard")
          .putHeaders(Header.Raw(ci"Host", "ADMIN.EXAMPLE.COM"))
      )
    )
    assertEquals(Status.Ok, exactByUriPort.status)
    assertEquals("admin:/dashboard", bodyText(exactByUriPort))

    val wildcard: Response[IO] = run(
      virtualHostApp(
        Request[IO](Method.GET, uri"/tenant")
          .putHeaders(Header.Raw(ci"Host", "shop.example.com"))
      )
    )
    assertEquals(Status.Ok, wildcard.status)
    assertEquals("wildcard:/tenant", bodyText(wildcard))

    val regex: Response[IO] = run(
      virtualHostApp(
        Request[IO](Method.GET, uri"/status")
          .putHeaders(Header.Raw(ci"Host", "api42.service.test"))
      )
    )
    assertEquals(Status.Ok, regex.status)
    assertEquals("regex:/status", bodyText(regex))

    val unknown: Response[IO] = run(
      virtualHostApp(
        Request[IO](Method.GET, uri"/missing")
          .putHeaders(Header.Raw(ci"Host", "unknown.example.net"))
      )
    )
    assertEquals(Status.NotFound, unknown.status)
    assertTrue(bodyText(unknown).contains("unknown.example.net"))

    val missingHost: Response[IO] = run(virtualHostApp(Request[IO](Method.GET, uri"/missing-host")))
    assertEquals(Status.BadRequest, missingHost.status)
    assertEquals("Host header required.", bodyText(missingHost))
  }

  @Test
  def basicAuthChallengeAcceptsValidCredentialsAndRejectsMissingOrInvalidCredentials(): Unit = {
    val challenge = BasicAuth.challenge[IO, String](
      "test-realm",
      credentials => IO.pure(Option.when(credentials.username == "alice" && credentials.password == "secret")(credentials.username)),
    )

    val accepted: Either[org.http4s.Challenge, AuthedRequest[IO, String]] = run(
      challenge(Request[IO](Method.GET, uri"/secure").putHeaders(Authorization(BasicCredentials("alice", "secret"))))
    )
    accepted match {
      case Right(authedRequest) =>
        assertEquals("alice", authedRequest.context)
        assertEquals(uri"/secure", authedRequest.req.uri)
      case Left(value) => throw new AssertionError(s"expected accepted credentials, got $value")
    }

    val rejected: Either[org.http4s.Challenge, AuthedRequest[IO, String]] = run(
      challenge(Request[IO](Method.GET, uri"/secure").putHeaders(Authorization(BasicCredentials("alice", "wrong"))))
    )
    assertEquals(Left(org.http4s.Challenge("Basic", "test-realm", Map("charset" -> "UTF-8"))), rejected)

    val missing: Either[org.http4s.Challenge, AuthedRequest[IO, String]] = run(
      challenge(Request[IO](Method.GET, uri"/secure"))
    )
    assertEquals(Left(org.http4s.Challenge("Basic", "test-realm", Map("charset" -> "UTF-8"))), missing)
  }
}

object Http4s_server_3Test {
  private given EntityDecoder[IO, String] = EntityDecoder.text[IO]

  private def pathInfoRoute(label: String): HttpRoutes[IO] = Kleisli { request =>
    OptionT.liftF(Response[IO](Status.Ok).withEntity(s"$label:${request.pathInfo.renderString}").pure[IO])
  }

  private def hostApp(label: String): HttpApp[IO] = HttpApp[IO] { request =>
    Response[IO](Status.Ok).withEntity(s"$label:${request.pathInfo.renderString}").pure[IO]
  }

  private def runRoutes(routes: HttpRoutes[IO], request: Request[IO]): Response[IO] =
    run(routes.orNotFound(request))

  private def bodyText(response: Response[IO]): String =
    run(response.as[String])

  private def rawHeader(response: Response[IO], name: CIString): Option[String] =
    response.headers.get(name).map(_.head.value)

  private def commaSeparatedHeaderValues(response: Response[IO], name: CIString): Set[String] =
    rawHeader(response, name).fold(Set.empty[String])(_.split(',').iterator.map(_.trim).filter(_.nonEmpty).toSet)

  private def hasCommaSeparatedHeaderValue(response: Response[IO], name: CIString, value: String): Boolean =
    commaSeparatedHeaderValues(response, name).exists(_.equalsIgnoreCase(value))

  private def run[A](io: IO[A]): A =
    io.timeout(5.seconds).unsafeRunSync()
}
