/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.core_3

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sttp.attributes.AttributeKey
import sttp.client4.*
import sttp.client4.ResponseException.DeserializationException
import sttp.client4.ResponseException.UnexpectedStatusCode
import sttp.client4.compression.Compressor
import sttp.client4.compression.Decompressor
import sttp.client4.httpurlconnection.HttpURLConnectionBackend
import sttp.client4.listener.ListenerBackend
import sttp.client4.listener.RequestListener
import sttp.client4.logging.Log
import sttp.client4.logging.LoggingBackend
import sttp.client4.logging.ResponseTimings
import sttp.client4.testing.RecordingBackend
import sttp.client4.testing.ResponseStub
import sttp.client4.testing.SyncBackendStub
import sttp.client4.wrappers.EitherBackend
import sttp.client4.wrappers.ResolveRelativeUrisBackend
import sttp.client4.wrappers.TryBackend
import sttp.model.*
import sttp.model.headers.Cookie
import sttp.shared.Identity

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future as JavaFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import scala.concurrent.duration.*
import scala.util.Success
import scala.util.Try

class Core_3Test {
  @Test
  def requestBuildersPreserveImmutableHeadersBodiesOptionsAndAttributes(): Unit = {
    val traceKey: AttributeKey[String] = new AttributeKey[String]("trace-id")
    val request: Request[Either[String, String]] = basicRequest
      .header("X-Trace", "first")
      .header("X-Trace", "second", DuplicateHeaderBehavior.Add)
      .header(Header.cookie(Cookie("a", "1")), DuplicateHeaderBehavior.Combine)
      .cookie("b", "2")
      .auth.bearer("secret-token")
      .proxyAuth.basic("proxy-user", "proxy-pass")
      .readTimeout(2.seconds)
      .followRedirects(false)
      .maxRedirects(3)
      .redirectToGet(true)
      .disableAutoDecompression
      .compressBody(Encodings.Gzip)
      .httpVersion(HttpVersion.HTTP_1_1)
      .maxResponseBodyLength(1024L)
      .loggingOptions(logRequestBody = Some(true), logResponseHeaders = Some(false))
      .attribute(traceKey, "trace-123")
      .body("hello", "UTF-8")
      .post(uri"https://api.example.com/search?q=scala native&token=secret")

    assertEquals(Method.POST, request.method)
    assertEquals("https://api.example.com/search?q=scala+native&token=secret", request.uri.toString)
    assertEquals(Seq("first", "second"), request.headers.filter(_.is("X-Trace")).map(_.value))
    assertEquals(Some("a=1; b=2"), request.header(HeaderNames.Cookie))
    assertEquals(Some("Bearer secret-token"), request.header(HeaderNames.Authorization))
    assertEquals(Some("Basic cHJveHktdXNlcjpwcm94eS1wYXNz"), request.header(HeaderNames.ProxyAuthorization))
    assertEquals(Some("gzip"), request.header(HeaderNames.ContentEncoding))
    assertEquals(Some("text/plain; charset=UTF-8"), request.header(HeaderNames.ContentType))
    assertEquals(Some(5L), request.contentLength)
    assertEquals(Some("trace-123"), request.attribute(traceKey))
    assertEquals(2.seconds, request.options.readTimeout)
    assertTrue(request.options.followRedirects)
    assertEquals(3, request.options.maxRedirects)
    assertTrue(request.options.redirectToGet)
    assertFalse(request.autoDecompressionEnabled)
    assertEquals(Some(Encodings.Gzip), request.options.compressRequestBody)
    assertEquals(Some(HttpVersion.HTTP_1_1), request.httpVersion)
    assertEquals(Some(1024L), request.maxResponseBodyLength)
    assertEquals(Some(true), request.loggingOptions.logRequestBody)
    assertEquals(Some(false), request.loggingOptions.logResponseHeaders)

    val StringBody(body, encoding, defaultContentType) = request.body: @unchecked
    assertEquals("hello", body)
    assertEquals("UTF-8", encoding)
    assertEquals(MediaType.TextPlain, defaultContentType)

    val shown: String = request.show(sensitiveQueryParams = Set("token"))
    assertTrue(shown.startsWith("POST https://api.example.com/search?q=scala+native&token=***"))
    assertTrue(shown.contains("Authorization: ***"))
    assertTrue(shown.contains("body: string: hello"))

    val curl: String = request.toCurl(sensitiveHeaders = Set(HeaderNames.Authorization), sensitiveQueryParams = Set("token"))
    assertTrue(curl.contains("curl"))
    assertTrue(curl.contains("token=***"))
    assertFalse(curl.contains("secret-token"))

    val rfc2616: String = request.toRfc2616Format(Set(HeaderNames.Authorization), Set("token"))
    assertTrue(rfc2616.contains("POST https://api.example.com/search?q=scala+native&token=***"))
    assertTrue(rfc2616.contains("Authorization: ***"))

    assertEquals(Method.POST, request.onlyMetadata.method)
    assertEquals(request.uri, request.onlyMetadata.uri)
    assertEquals(request.headers, request.onlyMetadata.headers)
  }

  @Test
  def bodyFactoriesSetExpectedContentTypesLengthsAndMultipartParts(): Unit = {
    val formRequest: Request[Either[String, String]] = emptyRequest
      .body(Seq("name" -> "Ada Lovelace", "lang" -> "Scala 3"), "UTF-8")
      .post(uri"https://example.com/form")
    assertEquals(Some("application/x-www-form-urlencoded"), formRequest.header(HeaderNames.ContentType))
    assertEquals(Some("name=Ada%20Lovelace&lang=Scala%203".length.toLong), formRequest.contentLength)
    assertEquals(StringBody("name=Ada%20Lovelace&lang=Scala%203", "UTF-8"), formRequest.body)

    val bytesRequest: Request[Either[String, String]] = emptyRequest.body(Array[Byte](1, 2, 3)).put(uri"https://example.com/bytes")
    assertEquals(Some("application/octet-stream"), bytesRequest.header(HeaderNames.ContentType))
    assertEquals(Some(3L), bytesRequest.contentLength)
    assertArrayEquals(Array[Byte](1, 2, 3), bytesRequest.body.asInstanceOf[ByteArrayBody].b)

    val buffer: ByteBuffer = ByteBuffer.wrap(Array[Byte](9, 8, 7, 6))
    val _ = buffer.get()
    val bufferRequest: Request[Either[String, String]] = emptyRequest.body(buffer).patch(uri"https://example.com/buffer")
    assertEquals(Some("application/octet-stream"), bufferRequest.header(HeaderNames.ContentType))
    assertEquals(Some(3L), bufferRequest.contentLength)
    assertSame(buffer, bufferRequest.body.asInstanceOf[ByteBufferBody].b)

    val stream: InputStream = new ByteArrayInputStream(Array[Byte](4, 5))
    val streamRequest: Request[Either[String, String]] = emptyRequest.body(stream).post(uri"https://example.com/stream")
    assertEquals(Some("application/octet-stream"), streamRequest.header(HeaderNames.ContentType))
    assertEquals(None, streamRequest.contentLength)
    assertSame(stream, streamRequest.body.asInstanceOf[InputStreamBody].b)

    val multipartRequest: Request[Either[String, String]] = emptyRequest
      .multipartBody(
        multipart("text", "hello"),
        multipart("bytes", Array[Byte](1, 2)).copy(otherDispositionParams = Map("filename" -> "data.bin")),
        multipart("params", Seq("a" -> "1", "b" -> "two words"))
      )
      .post(uri"https://example.com/upload")

    assertEquals(Some("multipart/form-data"), multipartRequest.header(HeaderNames.ContentType))
    val BasicMultipartBody(parts) = multipartRequest.body: @unchecked
    assertEquals(Seq("text", "bytes", "params"), parts.map(_.name))
    assertEquals(Some("text/plain; charset=utf-8"), parts.head.contentType)
    assertEquals(StringBody("hello", "utf-8"), parts.head.body)
    assertEquals(Map("filename" -> "data.bin"), parts(1).otherDispositionParams)
    assertArrayEquals(Array[Byte](1, 2), parts(1).body.asInstanceOf[ByteArrayBody].b)
    assertEquals("a=1&b=two%20words", parts(2).body.asInstanceOf[StringBody].s)
    assertTrue(multipartRequest.body.show.contains("multipart: text,bytes,params"))
  }

  @Test
  def responseDescriptionsTransformStubbedBodiesUsingMetadata(): Unit = {
    val metadataHeaders: Seq[Header] = Seq(Header.contentType(MediaType.TextPlain.charset("UTF-16")), Header("X-Mode", "success"))
    val utf16Bytes: Array[Byte] = "Zażółć".getBytes(StandardCharsets.UTF_16)
    val backend: SyncBackendStub = SyncBackendStub
      .whenRequestMatches(_.uri.path.contains("string"))
      .thenRespond(ResponseStub.adjust(utf16Bytes, StatusCode.Ok, metadataHeaders))
      .whenRequestMatches(_.uri.path.contains("params"))
      .thenRespondAdjust("a=1&b=two+words")
      .whenRequestMatches(_.uri.path.contains("not-found"))
      .thenRespondAdjust("missing", StatusCode.NotFound)
      .whenRequestMatches(_.uri.path.contains("both"))
      .thenRespondAdjust("left=right")

    val decoded: Response[String] = basicRequest
      .get(uri"https://example.com/string")
      .response(asStringAlways.mapWithMetadata((body: String, meta: ResponseMetadata) => s"${meta.header("X-Mode").get}:$body"))
      .send(backend)
    assertEquals("success:Zażółć", decoded.body)

    val params: Response[Either[String, Seq[(String, String)]]] = basicRequest
      .get(uri"https://example.com/params")
      .response(asParams)
      .send(backend)
    assertEquals(Right(Seq("a" -> "1", "b" -> "two+words")), params.body)

    val missing: Response[Either[String, String]] = basicRequest.get(uri"https://example.com/not-found").send(backend)
    assertEquals(Left("missing"), missing.body)
    assertEquals(StatusCode.NotFound, missing.code)

    val both: Response[(String, Option[Array[Byte]])] = basicRequest
      .get(uri"https://example.com/both")
      .response(asBothOption(asStringAlways, asByteArrayAlways))
      .send(backend)
    assertEquals("left=right", both.body._1)
    assertArrayEquals("left=right".getBytes(StandardCharsets.UTF_8), both.body._2.get)

    val selected: Response[String] = basicRequest
      .get(uri"https://example.com/not-found")
      .response(fromMetadata(asStringAlways.map(body => s"default:$body"), ConditionalResponseAs(_.code == StatusCode.NotFound, asStringAlways.map(body => s"missing:$body"))))
      .send(backend)
    assertEquals("missing:missing", selected.body)
  }

  @Test
  def responseFailureDescriptionsWrapUnexpectedStatusesAndDeserializationErrors(): Unit = {
    val backend: SyncBackendStub = SyncBackendStub
      .whenRequestMatches(_.uri.path.contains("bad-status"))
      .thenRespondAdjust("not good", StatusCode.BadRequest)
      .whenRequestMatches(_.uri.path.contains("bad-parse"))
      .thenRespondAdjust("not-an-int")

    val readFailure: SttpClientException.ReadException = assertThrows(
      classOf[SttpClientException.ReadException],
      () => basicRequest.get(uri"https://example.com/bad-status").response(asStringOrFail).send(backend)
    )
    val statusFailure: SttpClientException.ResponseHandlingException[?] = readFailure match {
      case e: SttpClientException.ResponseHandlingException[?] => e
      case other => throw new AssertionError(s"Expected response handling exception but got ${other.getClass.getName}")
    }
    val unexpected: UnexpectedStatusCode[?] = statusFailure.responseException match {
      case e: UnexpectedStatusCode[?] => e
      case other => throw new AssertionError(s"Expected unexpected status code but got ${other.getClass.getName}")
    }
    assertEquals(StatusCode.BadRequest, unexpected.response.code)
    assertEquals("not good", unexpected.body)
    assertEquals(Some(unexpected), ResponseException.find(statusFailure))

    val parsed: Response[Either[DeserializationException, Int]] = basicRequest
      .get(uri"https://example.com/bad-parse")
      .response(asStringAlways.mapWithMetadata(ResponseAs.deserializeCatchingExceptions(_.toInt)))
      .send(backend)
    val parseError: DeserializationException = parsed.body match {
      case Left(error) => error
      case Right(value) => throw new AssertionError(s"Expected deserialization error but got $value")
    }
    assertEquals("not-an-int", parseError.body)
    assertEquals(StatusCode.Ok, parseError.response.code)

    val thrown: DeserializationException = assertThrows(
      classOf[DeserializationException],
      () => ResponseAs.deserializeOrThrow((s: String) => Left(new IllegalArgumentException(s"bad: $s")))("payload", dummyMetadata(StatusCode.Ok))
    )
    assertEquals("payload", thrown.body)
    assertEquals("bad: payload", thrown.getMessage)

    val rightDeserialized: Either[ResponseException[String], Int] = ResponseAs
      .deserializeRightCatchingExceptions((s: String) => s.toInt)(Right("42"), dummyMetadata(StatusCode.Ok))
    assertEquals(Right(42), rightDeserialized)

    val leftDeserialized: Either[ResponseException[String], Int] = ResponseAs
      .deserializeRightWithError((s: String) => Right(s.toInt))(Left("http-error"), dummyMetadata(StatusCode.InternalServerError))
    val leftUnexpected: UnexpectedStatusCode[String] = leftDeserialized match {
      case Left(e: UnexpectedStatusCode[?]) => e.asInstanceOf[UnexpectedStatusCode[String]]
      case other => throw new AssertionError(s"Expected unexpected status code but got $other")
    }
    assertEquals("http-error", leftUnexpected.body)
  }

  @Test
  def backendStubsMatchRequestsCycleResponsesAndRecordInteractions(): Unit = {
    val bodyReceived: AtomicInteger = new AtomicInteger(0)
    val stub: SyncBackendStub = SyncBackendStub
      .whenRequestMatchesPartial { case request if request.method == Method.GET && request.uri.path.contains("cycle") =>
        ResponseStub.adjust(s"cycle-${request.uri.params.get("id").getOrElse("missing")}")
      }
      .whenRequestMatches(_.uri.path.contains("exact"))
      .thenRespondExact(Right("already typed"))
      .whenRequestMatches(_.uri.path.contains("loop"))
      .thenRespondCyclic(ResponseStub.adjust("first"), ResponseStub.adjust("second"))
      .whenAnyRequest
      .thenRespondNotFound()
    val recording: SyncBackend with RecordingBackend = RecordingBackend(stub)

    val cycle: Response[String] = basicRequest
      .get(uri"https://example.com/cycle?id=1")
      .response(asStringAlways)
      .onBodyReceived(_ => bodyReceived.incrementAndGet())
      .send(recording)
    assertEquals("cycle-1", cycle.body)
    assertEquals(1, bodyReceived.get())

    val exact: Response[Either[String, String]] = basicRequest.get(uri"https://example.com/exact").send(recording)
    assertEquals(Right("already typed"), exact.body)

    assertEquals("first", basicRequest.get(uri"https://example.com/loop").response(asStringAlways).send(recording).body)
    assertEquals("second", basicRequest.get(uri"https://example.com/loop").response(asStringAlways).send(recording).body)
    assertEquals("first", basicRequest.get(uri"https://example.com/loop").response(asStringAlways).send(recording).body)

    val notFound: Response[Either[String, String]] = basicRequest.get(uri"https://example.com/other").send(recording)
    assertEquals(StatusCode.NotFound, notFound.code)
    assertEquals(Left("Not Found"), notFound.body)

    assertEquals(6, recording.allInteractions.size)
    assertTrue(recording.allInteractions.forall(_._2.isSuccess))
    assertEquals(uri"https://example.com/cycle?id=1", recording.allInteractions.head._1.uri)
  }

  @Test
  def wrappersMapEffectsResolveRelativeUrisAndNotifyListeners(): Unit = {
    val events: java.util.List[String] = new java.util.concurrent.CopyOnWriteArrayList[String]()
    val baseStub: SyncBackendStub = SyncBackendStub
      .whenRequestMatches(_.uri.toString == "https://api.example.com/base/resource")
      .thenRespondAdjust("resolved")
      .whenRequestMatches(_.uri.path.contains("fail"))
      .thenThrow(new IllegalStateException("boom"))

    val listener: RequestListener[Identity, String] = new RequestListener[Identity, String] {
      override def before(request: GenericRequest[_, _]): String = {
        events.add(s"before:${request.uri}")
        "tag"
      }
      override def responseBodyReceived(request: GenericRequest[_, _], response: ResponseMetadata, tag: String): Unit =
        events.add(s"body:${response.code.code}:$tag")
      override def responseHandled(
          request: GenericRequest[_, _],
          response: ResponseMetadata,
          tag: String,
          exception: Option[ResponseException[_]]
      ): Unit = events.add(s"handled:${response.code.code}:${exception.isDefined}:$tag")
      override def exception(request: GenericRequest[_, _], tag: String, exception: Throwable, responseBodyReceivedCalled: Boolean): Unit =
        events.add(s"exception:${exception.getMessage}:$responseBodyReceivedCalled:$tag")
    }

    val listened: SyncBackend = ListenerBackend(ResolveRelativeUrisBackend(baseStub, uri"https://api.example.com/base/"), listener)
    val response: Response[String] = basicRequest
      .get(uri"resource")
      .response(asStringAlways)
      .send(listened)
    assertEquals("resolved", response.body)
    assertEquals(
      List("before:resource", "body:200:tag", "handled:200:false:tag"),
      events.toArray.toList
    )

    val tryBackend: Backend[Try] = TryBackend(baseStub)
    assertEquals(Success("resolved"), basicRequest.get(uri"https://api.example.com/base/resource").response(asStringAlways).send(tryBackend).map(_.body))
    assertTrue(basicRequest.get(uri"https://api.example.com/fail").response(asStringAlways).send(tryBackend).isFailure)

    val eitherBackend: Backend[[A] =>> Either[Throwable, A]] = EitherBackend(baseStub)
    assertEquals(Right("resolved"), basicRequest.get(uri"https://api.example.com/base/resource").response(asStringAlways).send(eitherBackend).map(_.body))
    assertThrows(
      classOf[IllegalStateException],
      () => basicRequest.get(uri"https://api.example.com/fail").response(asStringAlways).send(eitherBackend)
    )
  }

  @Test
  def loggingBackendUsesPublicLogCallbacksAndCanLogReplayableResponseBodies(): Unit = {
    val messages: java.util.List[String] = new java.util.concurrent.CopyOnWriteArrayList[String]()
    val log: Log[Identity] = new Log[Identity] {
      override def beforeRequestSend(request: GenericRequest[_, _]): Unit = messages.add(s"before:${request.showBasic}")
      override def response(
          request: GenericRequest[_, _],
          response: ResponseMetadata,
          responseBody: Option[String],
          timings: Option[ResponseTimings],
          exception: Option[ResponseException[_]]
      ): Unit = messages.add(s"response:${response.code.code}:${responseBody.getOrElse("<none>")}:${timings.isDefined}:${exception.isDefined}")
      override def requestException(request: GenericRequest[_, _], timing: Option[scala.concurrent.duration.Duration], exception: Throwable): Unit =
        messages.add(s"exception:${exception.getClass.getSimpleName}")
    }
    val stub: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust("log-body")
    val loggingBackend: SyncBackend = LoggingBackend(stub, log, includeTimings = true, logResponseBody = true)

    val response: Response[String] = basicRequest
      .get(uri"https://example.com/log")
      .response(asStringAlways)
      .send(loggingBackend)

    assertEquals("log-body", response.body)
    assertEquals(2, messages.size())
    assertEquals("before:GET https://example.com/log", messages.get(0))
    assertTrue(messages.get(1).startsWith("response:200:log-body:true:false"))
  }

  @Test
  def compressionHandlersCompressAndDecompressSupportedBasicBodies(): Unit = {
    val gzipCompressor: Compressor[Any] = Compressor.default[Any].find(_.encoding == Encodings.Gzip).get
    val deflateCompressor: Compressor[Any] = Compressor.default[Any].find(_.encoding == Encodings.Deflate).get

    val gzipBody: ByteArrayBody = gzipCompressor(StringBody("compress me", "UTF-8")).asInstanceOf[ByteArrayBody]
    assertEquals("compress me", new String(readAll(new GZIPInputStream(new ByteArrayInputStream(gzipBody.b))), StandardCharsets.UTF_8))

    val deflatedBody: ByteArrayBody = deflateCompressor(ByteArrayBody("deflate me".getBytes(StandardCharsets.UTF_8))).asInstanceOf[ByteArrayBody]
    assertEquals("deflate me", new String(readAll(new InflaterInputStream(new ByteArrayInputStream(deflatedBody.b))), StandardCharsets.UTF_8))

    val buffer: ByteBuffer = ByteBuffer.allocateDirect(4)
    val _ = buffer.put(Array[Byte](1, 2, 3, 4))
    val _ = buffer.flip()
    val compressedBuffer: ByteArrayBody = gzipCompressor(ByteBufferBody(buffer)).asInstanceOf[ByteArrayBody]
    assertArrayEquals(Array[Byte](1, 2, 3, 4), readAll(new GZIPInputStream(new ByteArrayInputStream(compressedBuffer.b))))

    val gzipInput: InputStreamBody = gzipCompressor(InputStreamBody(new ByteArrayInputStream("stream".getBytes(StandardCharsets.UTF_8)))).asInstanceOf[InputStreamBody]
    assertEquals("stream", new String(readAll(new GZIPInputStream(gzipInput.b)), StandardCharsets.UTF_8))

    val decompressed: InputStream = Decompressor.decompressIfPossible(
      new ByteArrayInputStream(gzipBody.b),
      Encodings.Gzip,
      Decompressor.defaultInputStream
    )
    assertEquals("compress me", new String(readAll(decompressed), StandardCharsets.UTF_8))

    val multipartError: IllegalArgumentException = assertThrows(
      classOf[IllegalArgumentException],
      () => gzipCompressor(BasicMultipartBody(Seq(multipart("field", "value"))))
    )
    assertEquals("Multipart bodies cannot be compressed", multipartError.getMessage)
  }

  @Test
  def realHttpUrlConnectionBackendSendsRequestBodiesAndParsesResponses(): Unit = {
    val captured: CapturedRequest = withOneShotHttpServer(
      responseBody = "answer=42&message=hello+world",
      responseHeaders = Seq(HeaderNames.ContentType -> "application/x-www-form-urlencoded; charset=UTF-8")
    ) { baseUrl =>
      val backend: SyncBackend = HttpURLConnectionBackend(BackendOptions.connectionTimeout(2.seconds))
      try {
        val response: Response[Seq[(String, String)]] = basicRequest
          .body(Seq("name" -> "Ada Lovelace", "lang" -> "Scala 3"), "UTF-8")
          .header("X-Test", "integration")
          .readTimeout(2.seconds)
          .post(uri"$baseUrl/submit?debug=true")
          .response(asParamsAlways)
          .send(backend)

        assertEquals(StatusCode.Ok, response.code)
        assertEquals(Seq("answer" -> "42", "message" -> "hello+world"), response.body)
      } finally {
        backend.close()
      }
    }

    assertEquals("POST", captured.method)
    assertEquals("/submit?debug=true", captured.target)
    assertEquals(Some("integration"), captured.headers.get("x-test"))
    assertEquals(Some("application/x-www-form-urlencoded"), captured.headers.get("content-type"))
    assertEquals("name=Ada%20Lovelace&lang=Scala%203", captured.body)
  }

  @Test
  def responseAndExceptionTypesExposePublicStateAndSafeRendering(): Unit = {
    val metadata: RequestMetadata = requestMetadata(Method.GET, uri"https://example.com/data?token=secret", Seq(Header.authorization("Bearer", "secret")))
    val history: List[ResponseMetadata] = List(dummyMetadata(StatusCode.MovedPermanently))
    val response: Response[String] = Response(
      "payload",
      StatusCode.Created,
      "Created",
      Seq(Header.authorization("Bearer", "secret"), Header("X-Trace", "visible")),
      history,
      metadata
    )

    assertEquals("payload", response.body)
    assertEquals(StatusCode.Created, response.code)
    assertEquals(history, response.history)
    assertEquals(metadata, response.request)
    assertEquals("201 Created, headers: Authorization: ***, X-Trace: visible, body: payload", response.show())
    assertEquals("201 Created", response.show(includeBody = false, includeHeaders = false))
    assertTrue(response.toString.contains("Authorization: ***"))

    val unexpected: UnexpectedStatusCode[String] = UnexpectedStatusCode("bad", response)
    assertEquals("bad", unexpected.body)
    assertEquals(response, unexpected.response)
    assertEquals("statusCode: 201, response: bad", unexpected.getMessage)

    val deserialization: DeserializationException = DeserializationException("raw", new IllegalArgumentException("invalid"), response)
    assertEquals("raw", deserialization.body)
    assertEquals("invalid", deserialization.getMessage)
    assertSame(response, deserialization.response)
    assertEquals(Some(deserialization), ResponseException.find(new RuntimeException("outer", deserialization)))

    val request: Request[Either[String, String]] = basicRequest.get(uri"https://example.com/data")
    val connect: SttpClientException.ConnectException = new SttpClientException.ConnectException(request, new java.net.ConnectException("refused"))
    assertSame(request, connect.request)
    assertTrue(connect.getMessage.contains("Exception when sending request: GET https://example.com/data"))

    val redirects: SttpClientException.TooManyRedirectsException = new SttpClientException.TooManyRedirectsException(request, 5)
    assertEquals(5, redirects.redirects)
    assertSame(request, redirects.request)
  }

  private case class CapturedRequest(method: String, target: String, headers: Map[String, String], body: String)

  private def withOneShotHttpServer(responseBody: String, responseHeaders: Seq[(String, String)])(f: String => Unit): CapturedRequest = {
    val serverSocket: ServerSocket = new ServerSocket(0)
    serverSocket.setSoTimeout(5000)
    val executor = Executors.newSingleThreadExecutor()
    val future: JavaFuture[CapturedRequest] = executor.submit(new Callable[CapturedRequest] {
      override def call(): CapturedRequest = serveOne(serverSocket, responseBody, responseHeaders)
    })

    try {
      f(s"http://127.0.0.1:${serverSocket.getLocalPort}")
      future.get(5, TimeUnit.SECONDS)
    } finally {
      serverSocket.close()
      executor.shutdownNow()
    }
  }

  private def serveOne(serverSocket: ServerSocket, responseBody: String, responseHeaders: Seq[(String, String)]): CapturedRequest = {
    val socket: Socket = serverSocket.accept()
    socket.setSoTimeout(5000)
    try {
      val reader: BufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream, StandardCharsets.ISO_8859_1))
      val requestLine: String = reader.readLine()
      val requestParts: Array[String] = requestLine.split(" ")
      val headers = scala.collection.mutable.LinkedHashMap.empty[String, String]
      var line: String = reader.readLine()
      while (line != null && line.nonEmpty) {
        val separator: Int = line.indexOf(':')
        if (separator >= 0) {
          headers += line.substring(0, separator).toLowerCase(java.util.Locale.ROOT) -> line.substring(separator + 1).trim
        }
        line = reader.readLine()
      }

      val contentLength: Int = headers.get("content-length").map(_.toInt).getOrElse(0)
      val bodyChars: Array[Char] = new Array[Char](contentLength)
      var offset: Int = 0
      while (offset < contentLength) {
        val read: Int = reader.read(bodyChars, offset, contentLength - offset)
        if (read < 0) throw new IllegalStateException("Unexpected end of request body")
        offset += read
      }

      val responseBytes: Array[Byte] = responseBody.getBytes(StandardCharsets.UTF_8)
      val headerBlock: String =
        (Seq(
          "HTTP/1.1 200 OK",
          s"Content-Length: ${responseBytes.length}",
          "Connection: close"
        ) ++ responseHeaders.map { case (name, value) => s"$name: $value" }).mkString("\r\n") + "\r\n\r\n"
      socket.getOutputStream.write(headerBlock.getBytes(StandardCharsets.ISO_8859_1))
      socket.getOutputStream.write(responseBytes)
      socket.getOutputStream.flush()

      CapturedRequest(requestParts(0), requestParts(1), headers.toMap, new String(bodyChars))
    } finally {
      socket.close()
    }
  }

  private def requestMetadata(method0: Method, uri0: Uri, headers0: Seq[Header]): RequestMetadata = new RequestMetadata {
    override def method: Method = method0
    override def uri: Uri = uri0
    override def headers: Seq[Header] = headers0
  }

  private def dummyMetadata(code0: StatusCode): ResponseMetadata = new ResponseMetadata {
    override def code: StatusCode = code0
    override def statusText: String = StatusText.default(code0).getOrElse("")
    override def headers: Seq[Header] = Nil
  }

  private def readAll(inputStream: InputStream): Array[Byte] = {
    try inputStream.readAllBytes()
    finally inputStream.close()
  }
}
