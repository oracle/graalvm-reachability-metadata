/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_server_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sttp.capabilities.Streams
import sttp.model.Header
import sttp.model.HeaderNames
import sttp.model.HasHeaders
import sttp.model.Method
import sttp.model.QueryParams
import sttp.model.StatusCode
import sttp.model.Uri
import sttp.model.Uri.UriContext
import sttp.monad.IdentityMonad
import sttp.monad.MonadError
import sttp.shared.Identity
import sttp.tapir.AttributeKey
import sttp.tapir.CodecFormat
import sttp.tapir.Endpoint
import sttp.tapir.PublicEndpoint
import sttp.tapir.RawBodyType
import sttp.tapir.TapirFile
import sttp.tapir.WebSocketBodyOutput
import sttp.tapir.auth
import sttp.tapir.endpoint
import sttp.tapir.header
import sttp.tapir.path
import sttp.tapir.paths
import sttp.tapir.query
import sttp.tapir.statusCode
import sttp.tapir.stringBody
import sttp.tapir.stringToPath
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interceptor.RequestInterceptor
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interceptor.content.NotAcceptableInterceptor
import sttp.tapir.server.interceptor.cors.CORSConfig
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureInterceptor
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.server.interceptor.exception.DefaultExceptionHandler
import sttp.tapir.server.interceptor.exception.ExceptionInterceptor
import sttp.tapir.server.interceptor.reject.DefaultRejectHandler
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.server.interpreter.RequestBody
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.server.model.ServerResponse

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.immutable.Seq
import scala.concurrent.duration.DurationInt
import scala.util.Success

class Tapir_server_3Test {
  private implicit val identityMonad: MonadError[Identity] = IdentityMonad
  private implicit val identityBodyListener: BodyListener[Identity, String] = new BodyListener[Identity, String] {
    override def onComplete(body: String)(cb: scala.util.Try[Unit] => Identity[Unit]): Identity[String] = {
      cb(Success(()))
      body
    }
  }

  @Test
  def serverInterpreterDecodesPathQueryAndHeadersAndEncodesResponseHeaders(): Unit = {
    val greetingEndpoint: PublicEndpoint[(String, Int), Unit, (String, String), Any] = endpoint.get
      .in("hello" / path[String]("name"))
      .in(query[Int]("times"))
      .out(stringBody.and(header[String]("X-Greeting")))
    val serverEndpoint: ServerEndpoint[Any, Identity] = greetingEndpoint.serverLogicSuccessPure[Identity] { case (name, times) =>
      (List.fill(times)(s"Hello, $name").mkString(" | "), s"$name:$times")
    }

    val response: ServerResponse[String] = responseFor(
      request(Method.GET, uri"http://example.test/hello/Ada?times=3"),
      List(serverEndpoint)
    )

    assertEquals(StatusCode.Ok, response.code)
    assertEquals("Hello, Ada | Hello, Ada | Hello, Ada", response.body.getOrElse(""))
    assertEquals(Some("Ada:3"), headerValue(response, "X-Greeting"))
  }

  @Test
  def serverInterpreterReadsStringRequestBodiesAndRendersTypedErrorOutputs(): Unit = {
    val validationEndpoint: PublicEndpoint[String, (StatusCode, String), String, Any] = endpoint.post
      .in("validate")
      .in(stringBody)
      .errorOut(statusCode.and(stringBody))
      .out(stringBody)
    val serverEndpoint: ServerEndpoint[Any, Identity] = validationEndpoint.serverLogicPure[Identity] { body =>
      if body.trim.nonEmpty then Right(s"accepted:${body.toUpperCase}")
      else Left((StatusCode.BadRequest, "empty body"))
    }

    val accepted: ServerResponse[String] = responseFor(
      request(Method.POST, uri"http://example.test/validate", body = "tapir", headers = Seq(Header(HeaderNames.ContentType, "text/plain"))),
      List(serverEndpoint)
    )
    val rejected: ServerResponse[String] = responseFor(
      request(Method.POST, uri"http://example.test/validate", body = "   ", headers = Seq(Header(HeaderNames.ContentType, "text/plain"))),
      List(serverEndpoint)
    )

    assertEquals(StatusCode.Ok, accepted.code)
    assertEquals("accepted:TAPIR", accepted.body.getOrElse(""))
    assertEquals(StatusCode.BadRequest, rejected.code)
    assertEquals("empty body", rejected.body.getOrElse(""))
  }

  @Test
  def wildcardPathCaptureProvidesAllRemainingSegmentsToServerLogic(): Unit = {
    val fileEndpoint: ServerEndpoint[Any, Identity] = endpoint.get
      .in("files" / paths)
      .out(stringBody)
      .serverLogicSuccessPure[Identity]((segments: List[String]) => s"requested:${segments.mkString("/")}")

    val response: ServerResponse[String] = responseFor(
      request(Method.GET, uri"http://example.test/files/docs/reference/readme.txt"),
      List(fileEndpoint)
    )

    assertEquals(StatusCode.Ok, response.code)
    assertEquals("requested:docs/reference/readme.txt", response.body.getOrElse(""))
  }

  @Test
  def bearerAuthenticationRunsSecurityLogicBeforeServerLogic(): Unit = {
    val securedEndpoint: Endpoint[String, Unit, (StatusCode, String), String, Any] = endpoint.get
      .in("bearer")
      .securityIn(auth.bearer[String]())
      .errorOut(statusCode.and(stringBody))
      .out(stringBody)
    val serverEndpoint: ServerEndpoint[Any, Identity] = securedEndpoint
      .serverSecurityLogicPure[String, Identity] {
        case "secret-token" => Right("api-user")
        case _              => Left((StatusCode.Unauthorized, "invalid bearer token"))
      }
      .serverLogicPure { user => _ => Right(s"authenticated:$user") }

    val accepted: ServerResponse[String] = responseFor(
      request(Method.GET, uri"http://example.test/bearer", headers = Seq(Header(HeaderNames.Authorization, "Bearer secret-token"))),
      List(serverEndpoint)
    )
    val rejected: ServerResponse[String] = responseFor(
      request(Method.GET, uri"http://example.test/bearer", headers = Seq(Header(HeaderNames.Authorization, "Bearer wrong-token"))),
      List(serverEndpoint)
    )

    assertEquals(StatusCode.Ok, accepted.code)
    assertEquals("authenticated:api-user", accepted.body.getOrElse(""))
    assertEquals(StatusCode.Unauthorized, rejected.code)
    assertEquals("invalid bearer token", rejected.body.getOrElse(""))
  }

  @Test
  def decodeFailureInterceptorTurnsMalformedInputsIntoBadRequestResponses(): Unit = {
    val doubleEndpoint: ServerEndpoint[Any, Identity] = endpoint.get
      .in("double")
      .in(query[Int]("value"))
      .out(stringBody)
      .serverLogicSuccessPure[Identity]((value: Int) => (value * 2).toString)

    val response: ServerResponse[String] = responseFor(
      request(Method.GET, uri"http://example.test/double?value=not-an-int"),
      List(doubleEndpoint)
    )

    assertEquals(StatusCode.BadRequest, response.code)
    assertTrue(response.body.getOrElse("").contains("Invalid value for: query parameter value"))
  }

  @Test
  def rejectInterceptorDistinguishesMissingRoutesFromMethodMismatches(): Unit = {
    val getEndpoint: ServerEndpoint[Any, Identity] = endpoint.get
      .in("resource")
      .out(stringBody)
      .serverLogicSuccessPure[Identity](_ => "get-resource")
    val healthEndpoint: ServerEndpoint[Any, Identity] = endpoint.get
      .in("health")
      .out(stringBody)
      .serverLogicSuccessPure[Identity](_ => "ok")

    val wrongMethod: ServerResponse[String] = responseFor(
      request(Method.POST, uri"http://example.test/resource"),
      List(getEndpoint, healthEndpoint)
    )
    val wrongPath: ServerResponse[String] = responseFor(
      request(Method.GET, uri"http://example.test/missing"),
      List(getEndpoint, healthEndpoint)
    )

    assertEquals(StatusCode.MethodNotAllowed, wrongMethod.code)
    assertEquals("Method Not Allowed", wrongMethod.body.getOrElse(""))
    assertEquals(StatusCode.NotFound, wrongPath.code)
    assertEquals("Not Found", wrongPath.body.getOrElse(""))
  }

  @Test
  def exceptionInterceptorConvertsUnhandledLogicExceptionsToServerResponses(): Unit = {
    val boomEndpoint: ServerEndpoint[Any, Identity] = endpoint.get
      .in("boom")
      .out(stringBody)
      .serverLogicSuccess[Identity] { _ =>
        throw new IllegalStateException("planned failure")
      }

    val response: ServerResponse[String] = responseFor(
      request(Method.GET, uri"http://example.test/boom"),
      List(boomEndpoint)
    )

    assertEquals(StatusCode.InternalServerError, response.code)
    assertEquals("Internal server error", response.body.getOrElse(""))
  }

  @Test
  def notAcceptableInterceptorShortCircuitsUnsupportedAcceptHeaders(): Unit = {
    val logicWasCalled: AtomicBoolean = new AtomicBoolean(false)
    val textEndpoint: ServerEndpoint[Any, Identity] = endpoint.get
      .in("text")
      .out(stringBody)
      .serverLogicSuccessPure[Identity] { _ =>
        logicWasCalled.set(true)
        "plain text"
      }

    val rejected: ServerResponse[String] = responseFor(
      request(Method.GET, uri"http://example.test/text", headers = Seq(Header(HeaderNames.Accept, "application/json"))),
      List(textEndpoint)
    )

    assertEquals(StatusCode.NotAcceptable, rejected.code)
    assertFalse(logicWasCalled.get())

    val accepted: ServerResponse[String] = responseFor(
      request(Method.GET, uri"http://example.test/text", headers = Seq(Header(HeaderNames.Accept, "text/plain"))),
      List(textEndpoint)
    )

    assertEquals(StatusCode.Ok, accepted.code)
    assertEquals("plain text", accepted.body.getOrElse(""))
    assertTrue(logicWasCalled.get())
  }

  @Test
  def corsInterceptorHandlesPreflightAndActualCorsRequests(): Unit = {
    val corsConfig: CORSConfig = CORSConfig.default
      .allowMatchingOrigins(_.endsWith(".example"))
      .allowCredentials
      .allowMethods(Method.GET, Method.POST)
      .reflectHeaders
      .exposeHeaders("X-Trace")
      .maxAge(5.seconds)
      .preflightResponseStatusCode(StatusCode.Ok)
    val corsInterceptor: CORSInterceptor[Identity] = CORSInterceptor.customOrThrow[Identity](corsConfig)
    val corsEndpoint: ServerEndpoint[Any, Identity] = endpoint.get
      .in("cors")
      .out(stringBody.and(header[String]("X-Trace")))
      .serverLogicSuccessPure[Identity](_ => ("cors-body", "trace-1"))

    val preflight: ServerResponse[String] = responseFor(
      request(
        Method.OPTIONS,
        uri"http://example.test/cors",
        headers = Seq(
          Header(HeaderNames.Origin, "https://client.example"),
          Header(HeaderNames.AccessControlRequestMethod, "POST"),
          Header(HeaderNames.AccessControlRequestHeaders, "X-Auth, X-Trace")
        )
      ),
      List(corsEndpoint),
      defaultInterceptors.prepended(corsInterceptor)
    )
    val actual: ServerResponse[String] = responseFor(
      request(Method.GET, uri"http://example.test/cors", headers = Seq(Header(HeaderNames.Origin, "https://client.example"))),
      List(corsEndpoint),
      defaultInterceptors.prepended(corsInterceptor)
    )

    assertEquals(StatusCode.Ok, preflight.code)
    assertEquals(Some("https://client.example"), headerValue(preflight, HeaderNames.AccessControlAllowOrigin))
    assertEquals(Some("true"), headerValue(preflight, HeaderNames.AccessControlAllowCredentials))
    assertTrue(headerValue(preflight, HeaderNames.AccessControlAllowMethods).exists(_.contains("POST")))
    assertTrue(headerValue(preflight, HeaderNames.AccessControlAllowHeaders).exists(_.contains("X-Auth")))
    assertEquals(Some("5"), headerValue(preflight, HeaderNames.AccessControlMaxAge))

    assertEquals(StatusCode.Ok, actual.code)
    assertEquals("cors-body", actual.body.getOrElse(""))
    assertEquals(Some("trace-1"), headerValue(actual, "X-Trace"))
    assertEquals(Some("https://client.example"), headerValue(actual, HeaderNames.AccessControlAllowOrigin))
    assertEquals(Some("true"), headerValue(actual, HeaderNames.AccessControlAllowCredentials))
    assertEquals(Some("X-Trace"), headerValue(actual, HeaderNames.AccessControlExposeHeaders))
  }

  @Test
  def corsConfigRejectsCredentialedWildcardConfiguration(): Unit = {
    val exception: IllegalArgumentException = assertThrows(
      classOf[IllegalArgumentException],
      () => CORSInterceptor.customOrThrow[Identity](CORSConfig.default.allowCredentials.allowAllOrigins)
    )

    assertTrue(exception.getMessage.contains("Illegal CORS config"))
  }

  @Test
  def requestInterceptorRewritesRequestBeforeEndpointMatching(): Unit = {
    val rewriteInterceptor: RequestInterceptor[Identity] = RequestInterceptor.transformServerRequest[Identity] { serverRequest =>
      serverRequest.withOverride(
        methodOverride = Some(Method.GET),
        uriOverride = Some(uri"http://example.test/intercepted?token=rewritten"),
        protocolOverride = None,
        connectionInfoOverride = None,
        pathSegmentsOverride = Some(List("intercepted")),
        queryParametersOverride = Some(QueryParams.fromSeq(Seq("token" -> "rewritten"))),
        headersOverride = Some(serverRequest.headers :+ Header("X-Intercepted", "yes"))
      )
    }
    val transformedEndpoint: ServerEndpoint[Any, Identity] = endpoint.get
      .in("intercepted")
      .in(query[String]("token"))
      .in(header[String]("X-Intercepted"))
      .out(stringBody)
      .serverLogicSuccessPure[Identity] { case (token, marker) => s"$token:$marker" }

    val response: ServerResponse[String] = responseFor(
      request(Method.POST, uri"http://example.test/original?token=original"),
      List(transformedEndpoint),
      defaultInterceptors.prepended(rewriteInterceptor)
    )

    assertEquals(StatusCode.Ok, response.code)
    assertEquals("rewritten:yes", response.body.getOrElse(""))
  }

  @Test
  def serverRequestOverridesAndAttributesPreserveRequestMetadata(): Unit = {
    val key: AttributeKey[String] = AttributeKey[String]
    val original: TestServerRequest = request(Method.GET, uri"http://example.test/original?first=1")
    val attributed: sttp.tapir.model.ServerRequest = original.attribute(key, "attribute-value")
    val overridden: sttp.tapir.model.ServerRequest = attributed.withOverride(
      methodOverride = Some(Method.POST),
      uriOverride = Some(uri"http://example.test/overridden?second=2"),
      protocolOverride = Some("HTTP/2"),
      connectionInfoOverride = Some(sttp.tapir.model.ConnectionInfo.NoInfo),
      pathSegmentsOverride = Some(List("overridden")),
      queryParametersOverride = Some(QueryParams.fromSeq(Seq("second" -> "2"))),
      headersOverride = Some(Seq(Header("X-Test", "yes")))
    )

    assertEquals(Some("attribute-value"), overridden.attribute(key))
    assertEquals(Method.POST, overridden.method)
    assertEquals(List("overridden"), overridden.pathSegments)
    assertEquals(Some("2"), overridden.queryParameters.get("second"))
    assertEquals(Some("yes"), overridden.header("X-Test"))
    assertEquals("HTTP/2", overridden.protocol)
    assertTrue(overridden.showShort.contains("/overridden?second=2"))
  }

  private val defaultInterceptors: List[Interceptor[Identity]] = List(
    new ExceptionInterceptor[Identity](DefaultExceptionHandler[Identity]),
    new DecodeFailureInterceptor[Identity](DefaultDecodeFailureHandler[Identity]),
    new NotAcceptableInterceptor[Identity],
    new RejectInterceptor[Identity](DefaultRejectHandler.orNotFound[Identity])
  )

  private def responseFor(
      serverRequest: TestServerRequest,
      serverEndpoints: List[ServerEndpoint[Any, Identity]],
      interceptors: List[Interceptor[Identity]] = defaultInterceptors
  ): ServerResponse[String] = {
    interpreter(serverEndpoints, interceptors)(serverRequest) match {
      case RequestResult.Response(response, _) => response
      case RequestResult.Failure(failures)  => throw new AssertionError(s"Expected response, got failures: $failures")
    }
  }

  private def interpreter(
      serverEndpoints: List[ServerEndpoint[Any, Identity]],
      interceptors: List[Interceptor[Identity]]
  ): ServerInterpreter[Any, Identity, String, NoStreams] =
    new ServerInterpreter[Any, Identity, String, NoStreams](
      _ => serverEndpoints,
      StringRequestBody,
      StringToResponseBody,
      interceptors,
      (_: TapirFile) => ()
    )

  private def request(
      method: Method,
      uri: Uri,
      body: String = "",
      headers: Seq[Header] = Seq.empty
  ): TestServerRequest =
    TestServerRequest(method, uri, headers, body)

  private def headerValue(response: ServerResponse[String], name: String): Option[String] =
    response.headers.find(_.is(name)).map(_.value)

  private trait NoStreams

  private object NoStreamCapabilities extends Streams[NoStreams] {
    override type BinaryStream = Nothing
    override type Pipe[A, B] = Nothing
  }

  private object StringRequestBody extends RequestBody[Identity, NoStreams] {
    override val streams: Streams[NoStreams] = NoStreamCapabilities

    override def toRaw[R](serverRequest: sttp.tapir.model.ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): Identity[RawValue[R]] = {
      val requestBody: String = serverRequest.underlying.asInstanceOf[String]
      val value: Any = bodyType match {
        case RawBodyType.StringBody(_)  => requestBody
        case RawBodyType.ByteArrayBody  => requestBody.getBytes(StandardCharsets.UTF_8)
        case RawBodyType.ByteBufferBody => ByteBuffer.wrap(requestBody.getBytes(StandardCharsets.UTF_8))
        case RawBodyType.InputStreamBody =>
          new java.io.ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8))
        case other => throw new UnsupportedOperationException(s"Unsupported request body type in test: $other")
      }
      RawValue(value.asInstanceOf[R])
    }

    override def toStream(serverRequest: sttp.tapir.model.ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
      throw new UnsupportedOperationException("Streaming request bodies are not used by these tests")
  }

  private object StringToResponseBody extends ToResponseBody[String, NoStreams] {
    override val streams: Streams[NoStreams] = NoStreamCapabilities

    override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): String =
      bodyType match {
        case RawBodyType.StringBody(_)  => v.asInstanceOf[String]
        case RawBodyType.ByteArrayBody  => new String(v.asInstanceOf[Array[Byte]], StandardCharsets.UTF_8)
        case RawBodyType.ByteBufferBody => StandardCharsets.UTF_8.decode(v.asInstanceOf[ByteBuffer]).toString
        case RawBodyType.InputStreamBody =>
          new String(v.asInstanceOf[InputStream].readAllBytes(), StandardCharsets.UTF_8)
        case other => throw new UnsupportedOperationException(s"Unsupported response body type in test: $other")
      }

    override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): String =
      throw new UnsupportedOperationException("Streaming response bodies are not used by these tests")

    override def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
    ): String =
      throw new UnsupportedOperationException("WebSocket responses are not used by these tests")
  }

  private final case class TestServerRequest(
      method: Method,
      uri: Uri,
      headers: Seq[Header],
      body: String,
      protocol: String = "HTTP/1.1",
      connectionInfo: sttp.tapir.model.ConnectionInfo = sttp.tapir.model.ConnectionInfo.NoInfo,
      attributes: Map[AttributeKey[_], Any] = Map.empty
  ) extends sttp.tapir.model.ServerRequest {
    override def underlying: Any = body
    override def pathSegments: List[String] = uri.path.filter(_.nonEmpty).toList
    override def queryParameters: QueryParams = uri.params
    override def attribute[T](k: AttributeKey[T]): Option[T] = attributes.get(k).map(_.asInstanceOf[T])
    override def attribute[T](k: AttributeKey[T], v: T): sttp.tapir.model.ServerRequest = copy(attributes = attributes.updated(k, v))
    override def withUnderlying(underlying: Any): sttp.tapir.model.ServerRequest = copy(body = underlying.asInstanceOf[String])
  }
}
