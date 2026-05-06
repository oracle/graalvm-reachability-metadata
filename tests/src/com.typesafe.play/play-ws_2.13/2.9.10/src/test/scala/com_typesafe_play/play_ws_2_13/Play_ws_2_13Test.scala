/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_ws_2_13

import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.Charset

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.xml.Elem

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.ws.BodyWritable
import play.api.libs.ws.DefaultWSCookie
import play.api.libs.ws.DefaultWSProxyServer
import play.api.libs.ws.EmptyBody
import play.api.libs.ws.InMemoryBody
import play.api.libs.ws.SourceBody
import play.api.libs.ws.WSAuthScheme
import play.api.libs.ws.WSBody
import play.api.libs.ws.WSBodyReadables
import play.api.libs.ws.WSBodyWritables
import play.api.libs.ws.WSCookie
import play.api.libs.ws.WSClient
import play.api.libs.ws.WSProxyServer
import play.api.libs.ws.WSRequest
import play.api.libs.ws.WSRequestExecutor
import play.api.libs.ws.WSRequestFilter
import play.api.libs.ws.WSResponse
import play.api.libs.ws.WSSignatureCalculator
import play.api.mvc.MultipartFormData

class Play_ws_2_13Test {
  @Test
  def bodyWritablesCreateExpectedBodyRepresentations(): Unit = {
    val writables: WSBodyWritables = WSBodyWritables

    assertInMemoryBody("plain text", "text/plain", writables.writeableOf_String)
    assertInMemoryBody(new StringBuilder("builder text"), "text/plain", writables.writeableOf_StringBuilder)
    assertInMemoryBody(ByteString(1, 2, 3), "application/octet-stream", writables.writeableOf_Bytes)
    assertInMemoryBody(Array[Byte](4, 5, 6), "application/octet-stream", writables.writeableOf_ByteArray)
    assertInMemoryBody(ByteBuffer.wrap(Array[Byte](7, 8)), "application/octet-stream", writables.writeableOf_ByteBuffer)

    val formBody: InMemoryBody = writables.writeableOf_urlEncodedForm
      .transform(Map("name" -> Seq("Ada Lovelace"), "lang" -> Seq("Scala")))
      .asInstanceOf[InMemoryBody]
    assertEquals("application/x-www-form-urlencoded", writables.writeableOf_urlEncodedForm.contentType)
    assertEquals(Set("name=Ada+Lovelace", "lang=Scala"), formBody.bytes.utf8String.split('&').toSet)

    val simpleFormBody: InMemoryBody = writables.writeableOf_urlEncodedSimpleForm
      .transform(Map("answer" -> "42"))
      .asInstanceOf[InMemoryBody]
    assertEquals("answer=42", simpleFormBody.bytes.utf8String)

    val json: JsValue = Json.obj("library" -> "play-ws", "active" -> true)
    val jsonBody: InMemoryBody = writables.writeableOf_JsValue.transform(json).asInstanceOf[InMemoryBody]
    assertEquals("application/json", writables.writeableOf_JsValue.contentType)
    assertEquals(json, Json.parse(jsonBody.bytes.toArray))

    val xml: Elem = <request><id>123</id></request>
    val xmlWritable: BodyWritable[Elem] = writables.writeableOf_NodeSeq[Elem]
    val xmlBody: InMemoryBody = xmlWritable.transform(xml).asInstanceOf[InMemoryBody]
    assertEquals("text/xml", xmlWritable.contentType)
    assertEquals(xml.toString(), xmlBody.bytes.utf8String)

    val multipartSource: Source[MultipartFormData.Part[Source[ByteString, _]], _] = Source.empty
    val multipartWritable: BodyWritable[Source[MultipartFormData.Part[Source[ByteString, _]], _]] =
      writables.bodyWritableOf_Multipart
    val multipartBody: WSBody = multipartWritable.transform(multipartSource)
    assertTrue(multipartBody.isInstanceOf[SourceBody])
    assertTrue(multipartWritable.contentType.startsWith("multipart/form-data; boundary="))
  }

  @Test
  def responseHelpersReadHeadersCookiesAndTypedBodies(): Unit = {
    val cookie: WSCookie = DefaultWSCookie(
      name = "session",
      value = "abc",
      domain = Some("example.test"),
      path = Some("/"),
      maxAge = Some(30L),
      secure = true,
      httpOnly = true
    )
    val jsonBody: ByteString = ByteString.fromString("{\"message\":\"hello\",\"count\":2}")
    val response: WSResponse = TestWSResponse(
      status = 202,
      statusText = "Accepted",
      bytes = jsonBody,
      headers = Map("Content-Type" -> Seq("application/json; charset=UTF-8"), "X-Trace" -> Seq("a", "b")),
      cookies = Seq(cookie),
      underlyingValue = "underlying-response"
    )

    assertEquals(202, response.status)
    assertEquals("Accepted", response.statusText)
    assertEquals("application/json; charset=UTF-8", response.contentType)
    assertEquals(Some("a"), response.header("X-Trace"))
    assertEquals(Seq("a", "b"), response.headerValues("X-Trace"))
    assertEquals(Seq.empty, response.headerValues("Missing"))
    assertEquals(Some(cookie), response.cookie("session"))
    assertEquals(None, response.cookie("missing"))
    assertEquals("underlying-response", response.underlying[String])

    assertEquals(jsonBody.utf8String, response.body)
    assertEquals(jsonBody, response.body[ByteString](WSBodyReadables.readableAsByteString))
    assertArrayEquals(jsonBody.toArray, response.body[Array[Byte]](WSBodyReadables.readableAsByteArray))
    assertEquals(Json.obj("message" -> "hello", "count" -> 2), response.body[JsValue](WSBodyReadables.readableAsJson))
    assertEquals(Json.obj("message" -> "hello", "count" -> 2), response.json)

    val buffer: ByteBuffer = response.body[ByteBuffer](WSBodyReadables.readableAsByteBuffer)
    val bufferBytes: Array[Byte] = new Array[Byte](buffer.remaining())
    buffer.get(bufferBytes)
    assertArrayEquals(jsonBody.toArray, bufferBytes)

    val xmlResponse: WSResponse = TestWSResponse(
      bytes = ByteString.fromString("<root><child>value</child></root>"),
      headers = Map("Content-Type" -> Seq("application/xml"))
    )
    assertEquals("value", (xmlResponse.body[Elem](WSBodyReadables.readableAsXml) \ "child").text)
    assertEquals("value", (xmlResponse.xml \ "child").text)

    val defaultContentTypeResponse: WSResponse = TestWSResponse(bytes = ByteString.fromString("binary"))
    assertEquals("application/octet-stream", defaultContentTypeResponse.contentType)
  }

  @Test
  def requestBuilderKeepsImmutableRequestState(): Unit = {
    val client: RecordingWSClient = new RecordingWSClient
    val proxyServer: WSProxyServer = DefaultWSProxyServer(
      host = "proxy.example.test",
      port = 8080,
      protocol = Some("http"),
      principal = Some("user"),
      password = Some("secret"),
      nonProxyHosts = Some(Seq("localhost"))
    )
    val signatureCalculator: WSSignatureCalculator = new WSSignatureCalculator {}
    val original: WSRequest = client.url("https://example.test/search")
    val updated: WSRequest = original
      .withHttpHeaders("Accept" -> "application/json")
      .addHttpHeaders("X-Trace" -> "one", "X-Trace" -> "two")
      .withQueryStringParameters("q" -> "native image")
      .addQueryStringParameters("page" -> "1", "page" -> "2")
      .withCookies(DefaultWSCookie("first", "1"))
      .addCookies(DefaultWSCookie("second", "2"))
      .withAuth("alice", "wonderland", WSAuthScheme.BASIC)
      .withFollowRedirects(false)
      .withRequestTimeout(3.seconds)
      .withVirtualHost("virtual.example.test")
      .withProxyServer(proxyServer)
      .sign(signatureCalculator)
      .withBody("payload")(WSBodyWritables.writeableOf_String)

    assertEquals(Map.empty, original.headers)
    assertEquals(Map.empty, original.queryString)
    assertEquals(EmptyBody, original.body)
    assertEquals(Some("application/json"), updated.header("Accept"))
    assertEquals(Seq("one", "two"), updated.headerValues("X-Trace"))
    assertEquals(Seq("native image"), updated.queryString("q"))
    assertEquals(Seq("1", "2"), updated.queryString("page"))
    assertEquals(Seq("first", "second"), updated.cookies.map(_.name))
    assertEquals(Some(("alice", "wonderland", WSAuthScheme.BASIC)), updated.auth)
    assertEquals(Some(false), updated.followRedirects)
    assertEquals(Some(3.seconds), updated.requestTimeout)
    assertEquals(Some("virtual.example.test"), updated.virtualHost)
    assertEquals(Some(proxyServer), updated.proxyServer)
    assertEquals(Some(signatureCalculator), updated.calc)
    assertEquals(Some("text/plain"), updated.contentType)
    assertEquals("payload", updated.body.asInstanceOf[InMemoryBody].bytes.utf8String)
    assertSame(client, client.underlying[RecordingWSClient])

    assertFalse(client.closed)
    client.close()
    assertTrue(client.closed)
  }

  @Test
  def executionMethodsSetHttpMethodAndBody(): Unit = {
    val request: WSRequest = new RecordingWSClient().url("https://example.test/resource")

    assertExecuted(request.get(), "GET", EmptyBody)
    assertExecuted(request.delete(), "DELETE", EmptyBody)
    assertExecuted(request.head(), "HEAD", EmptyBody)
    assertExecuted(request.options(), "OPTIONS", EmptyBody)
    assertExecuted(request.execute("TRACE"), "TRACE", EmptyBody)

    assertExecuted(
      request.post("created")(WSBodyWritables.writeableOf_String),
      "POST",
      InMemoryBody(ByteString.fromString("created"))
    )
    assertExecuted(
      request.put(ByteString.fromString("updated"))(WSBodyWritables.writeableOf_Bytes),
      "PUT",
      InMemoryBody(ByteString.fromString("updated"))
    )
    assertExecuted(
      request.patch(Array[Byte](9, 10))(WSBodyWritables.writeableOf_ByteArray),
      "PATCH",
      InMemoryBody(ByteString(9, 10))
    )

    val tempFile: File = File.createTempFile("play-ws-body", ".txt")
    try {
      assertTrue(Await.result(request.post(tempFile), 5.seconds).underlying[WSRequest].body.isInstanceOf[SourceBody])
      assertTrue(Await.result(request.put(tempFile), 5.seconds).underlying[WSRequest].body.isInstanceOf[SourceBody])
      assertTrue(Await.result(request.patch(tempFile), 5.seconds).underlying[WSRequest].body.isInstanceOf[SourceBody])
    } finally {
      assertTrue(tempFile.delete() || !tempFile.exists())
    }
  }

  @Test
  def requestFiltersCanTransformRequestsBeforeExecution(): Unit = {
    val filter: WSRequestFilter = WSRequestFilter { executor: WSRequestExecutor =>
      WSRequestExecutor { request =>
        executor(request.addHttpHeaders("X-Filtered" -> "true").withMethod("PATCH"))
      }
    }
    val request: WSRequest = new RecordingWSClient()
      .url("https://example.test/filter")
      .withRequestFilter(filter)
      .withMethod("POST")

    val response: WSResponse = Await.result(request.execute(), 5.seconds)
    val executedRequest: WSRequest = response.underlying[WSRequest]

    assertEquals("PATCH", executedRequest.method)
    assertEquals(Some("true"), executedRequest.header("X-Filtered"))
    assertEquals(Some("PATCH"), response.header("X-Executed-Method"))
  }

  private def assertExecuted(result: Future[WSResponse], expectedMethod: String, expectedBody: WSBody): Unit = {
    val response: WSResponse = Await.result(result, 5.seconds)
    val request: WSRequest = response.underlying[WSRequest]
    assertEquals(expectedMethod, request.method)
    assertEquals(expectedBody, request.body)
    assertEquals(Some(expectedMethod), response.header("X-Executed-Method"))
  }

  private def assertInMemoryBody[T](value: T, contentType: String, writable: BodyWritable[T]): Unit = {
    val expectedBytes: Option[Array[Byte]] = value match {
      case byteBuffer: ByteBuffer =>
        val duplicate: ByteBuffer = byteBuffer.duplicate()
        val bytes: Array[Byte] = new Array[Byte](duplicate.remaining())
        duplicate.get(bytes)
        Some(bytes)
      case _ => None
    }
    val body: InMemoryBody = writable.transform(value).asInstanceOf[InMemoryBody]
    assertEquals(contentType, writable.contentType)
    value match {
      case string: String => assertEquals(string, body.bytes.utf8String)
      case builder: StringBuilder => assertEquals(builder.toString(), body.bytes.utf8String)
      case byteString: ByteString => assertEquals(byteString, body.bytes)
      case bytes: Array[Byte] => assertArrayEquals(bytes, body.bytes.toArray)
      case _: ByteBuffer => assertArrayEquals(expectedBytes.get, body.bytes.toArray)
      case other => throw new AssertionError(s"Unsupported assertion value: $other")
    }
  }

  private final class RecordingWSClient extends WSClient {
    var closed: Boolean = false

    override def underlying[T]: T = this.asInstanceOf[T]

    override def url(url: String): WSRequest = RecordingWSRequest(urlValue = url)

    override def close(): Unit = {
      closed = true
    }
  }

  private final case class RecordingWSRequest(
      urlValue: String,
      methodValue: String = "GET",
      bodyValue: WSBody = EmptyBody,
      contentTypeValue: Option[String] = None,
      headersValue: Map[String, Seq[String]] = Map.empty,
      queryStringValue: Map[String, Seq[String]] = Map.empty,
      cookiesValue: Seq[WSCookie] = Seq.empty,
      calcValue: Option[WSSignatureCalculator] = None,
      authValue: Option[(String, String, WSAuthScheme)] = None,
      followRedirectsValue: Option[Boolean] = None,
      requestTimeoutValue: Option[Duration] = None,
      virtualHostValue: Option[String] = None,
      proxyServerValue: Option[WSProxyServer] = None,
      filters: Vector[WSRequestFilter] = Vector.empty,
      disableUrlEncoding: Boolean = false
  ) extends WSRequest {
    override def url: String = urlValue

    override def uri: URI = URI.create(urlValue)

    override def contentType: Option[String] = contentTypeValue

    override def method: String = methodValue

    override def body: WSBody = bodyValue

    override def headers: Map[String, Seq[String]] = headersValue

    override def queryString: Map[String, Seq[String]] = queryStringValue

    override def cookies: Seq[WSCookie] = cookiesValue

    override def calc: Option[WSSignatureCalculator] = calcValue

    override def auth: Option[(String, String, WSAuthScheme)] = authValue

    override def followRedirects: Option[Boolean] = followRedirectsValue

    override def requestTimeout: Option[Duration] = requestTimeoutValue

    override def virtualHost: Option[String] = virtualHostValue

    override def proxyServer: Option[WSProxyServer] = proxyServerValue

    override def sign(calc: WSSignatureCalculator): WSRequest = copy(calcValue = Some(calc))

    override def withAuth(username: String, password: String, scheme: WSAuthScheme): WSRequest =
      copy(authValue = Some((username, password, scheme)))

    override def withHeaders(headers: (String, String)*): WSRequest = addHttpHeaders(headers: _*)

    override def withHttpHeaders(headers: (String, String)*): WSRequest =
      copy(headersValue = grouped(headers))

    override def withQueryString(parameters: (String, String)*): WSRequest = addQueryStringParameters(parameters: _*)

    override def withQueryStringParameters(parameters: (String, String)*): WSRequest =
      copy(queryStringValue = grouped(parameters))

    override def addCookies(cookies: WSCookie*): WSRequest = copy(cookiesValue = cookiesValue ++ cookies)

    override def withCookies(cookie: WSCookie*): WSRequest = copy(cookiesValue = cookie)

    override def withFollowRedirects(follow: Boolean): WSRequest = copy(followRedirectsValue = Some(follow))

    override def withDisableUrlEncoding(disableUrlEncoding: Boolean): WSRequest =
      copy(disableUrlEncoding = disableUrlEncoding)

    override def withRequestTimeout(timeout: Duration): WSRequest = copy(requestTimeoutValue = Some(timeout))

    override def withRequestFilter(filter: WSRequestFilter): WSRequest = copy(filters = filters :+ filter)

    override def withVirtualHost(vh: String): WSRequest = copy(virtualHostValue = Some(vh))

    override def withProxyServer(proxyServer: WSProxyServer): WSRequest = copy(proxyServerValue = Some(proxyServer))

    override def withUrl(url: String): WSRequest = copy(urlValue = url)

    override def withMethod(method: String): WSRequest = copy(methodValue = method)

    override def withBody[T: BodyWritable](body: T): WSRequest = {
      val writable: BodyWritable[T] = implicitly[BodyWritable[T]]
      copy(bodyValue = writable.transform(body), contentTypeValue = Some(writable.contentType))
    }

    override def get(): Future[WSResponse] = execute("GET")

    override def post[T: BodyWritable](body: T): Future[WSResponse] = withBody(body).execute("POST")

    override def post(body: File): Future[WSResponse] = withBody(body).execute("POST")

    override def post(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[WSResponse] =
      withBody(body).execute("POST")

    override def patch[T: BodyWritable](body: T): Future[WSResponse] = withBody(body).execute("PATCH")

    override def patch(body: File): Future[WSResponse] = withBody(body).execute("PATCH")

    override def patch(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[WSResponse] =
      withBody(body).execute("PATCH")

    override def put[T: BodyWritable](body: T): Future[WSResponse] = withBody(body).execute("PUT")

    override def put(body: File): Future[WSResponse] = withBody(body).execute("PUT")

    override def put(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[WSResponse] =
      withBody(body).execute("PUT")

    override def delete(): Future[WSResponse] = execute("DELETE")

    override def head(): Future[WSResponse] = execute("HEAD")

    override def options(): Future[WSResponse] = execute("OPTIONS")

    override def execute(method: String): Future[WSResponse] = withMethod(method).execute()

    override def execute(): Future[WSResponse] = {
      val baseExecutor: WSRequestExecutor = WSRequestExecutor { request =>
        Future.successful(
          TestWSResponse(
            headers = Map("X-Executed-Method" -> Seq(request.method)),
            underlyingValue = request.asInstanceOf[WSRequest]
          )
        )
      }
      val executor: WSRequestExecutor = filters.foldRight(baseExecutor) { (filter, next) =>
        filter(next)
      }
      executor(this).asInstanceOf[Future[WSResponse]]
    }

    override def stream(): Future[WSResponse] = execute()

    private def grouped(values: Seq[(String, String)]): Map[String, Seq[String]] =
      values.groupMap(_._1)(_._2)
  }

  private final case class TestWSResponse(
      status: Int = 200,
      statusText: String = "OK",
      bytes: ByteString = ByteString.empty,
      headers: Map[String, Seq[String]] = Map.empty,
      cookies: Seq[WSCookie] = Seq.empty,
      underlyingValue: Any = ()
  ) extends WSResponse {
    override def uri: URI = URI.create("https://example.test/response")

    override def underlying[T]: T = underlyingValue.asInstanceOf[T]

    override def cookie(name: String): Option[WSCookie] = cookies.find(_.name == name)

    override def body: String = {
      val encoding: Charset = Charset.forName(
        header("Content-Type")
          .flatMap(_.split(';').iterator.map(_.trim).find(_.toLowerCase.startsWith("charset=")))
          .map(_.substring("charset=".length))
          .getOrElse(if (contentType.startsWith("text/")) "ISO-8859-1" else "UTF-8")
      )
      new String(bytes.toArray, encoding)
    }

    override def bodyAsBytes: ByteString = bytes

    override def bodyAsSource: Source[ByteString, _] = Source.single(bytes)

    override def allHeaders: Map[String, Seq[String]] = headers

    override def xml: Elem = body[Elem](readableAsXml)

    override def json: JsValue = body[JsValue](readableAsJson)
  }
}
