/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_http_3

import java.nio.charset.StandardCharsets

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.Chunk
import zio.Duration
import zio.Runtime
import zio.Unsafe
import zio.ZIO
import zio.http.Body
import zio.http.Boundary
import zio.http.Cookie
import zio.http.Form
import zio.http.FormField
import zio.http.Handler
import zio.http.Header
import zio.http.Headers
import zio.http.MediaType
import zio.http.Method
import zio.http.Path
import zio.http.QueryParams
import zio.http.Request
import zio.http.Response
import zio.http.Routes
import zio.http.Status
import zio.http.URL
import zio.http.WebSocketFrame
import zio.http.handler
import zio.http.int
import zio.stream.ZStream

@Timeout(10)
class Zio_http_3Test {
  @Test
  def bodySupportsTextStreamsAndForms(): Unit = {
    val textBody: Body = Body.fromString("hello zio-http", StandardCharsets.UTF_8).contentType(MediaType.text.plain)
    assertThat(textBody.mediaType).isEqualTo(Some(MediaType.text.plain))
    assertThat(textBody.knownContentLength).isEqualTo(Some(14L))
    assertThat(unsafeRun(textBody.asString(StandardCharsets.UTF_8))).isEqualTo("hello zio-http")

    val streamedBody: Body = Body.fromStreamChunked(
      ZStream.fromChunk(Chunk.fromArray("streamed".getBytes(StandardCharsets.UTF_8))),
    )
    val collectedStream: Body = unsafeRun(streamedBody.materialize)
    assertThat(unsafeRun(collectedStream.asString(StandardCharsets.UTF_8))).isEqualTo("streamed")
    assertThat(collectedStream.isComplete).isTrue()

    val form: Form = Form.fromStrings("name" -> "zio", "kind" -> "http")
    val encodedFormBody: Body = Body.fromURLEncodedForm(form)
    val decodedForm: Form = unsafeRun(encodedFormBody.asURLEncodedForm)
    assertThat(decodedForm.get("name").contains(FormField.Simple("name", "zio"))).isTrue()
    assertThat(decodedForm.toQueryParams.getAll("kind")).isEqualTo(Chunk("http"))
  }

  @Test
  def multipartFormsEncodeAndDecodeTextAndBinaryParts(): Unit = {
    val binaryData: Chunk[Byte] = Chunk.fromArray("file-content".getBytes(StandardCharsets.UTF_8))
    val form: Form = Form(
      Chunk[FormField](
        FormField.simpleField("description", "native friendly"),
        FormField.binaryField("attachment", binaryData, MediaType.text.plain, None, Some("note.txt")),
      ),
    )
    val boundary: Boundary = Boundary("zio-http-test-boundary")
    val multipartBody: Body = Body.fromMultipartForm(form, boundary)

    assertThat(multipartBody.mediaType).isEqualTo(Some(MediaType.multipart.`form-data`))
    assertThat(multipartBody.contentType.flatMap(_.boundary).map(_.id)).isEqualTo(Some("zio-http-test-boundary"))

    val decodedForm: Form = unsafeRun(multipartBody.asMultipartForm)
    assertThat(decodedForm.formData.size).isEqualTo(2)
    assertThat(unsafeRun(decodedForm.get("description").get.asText)).isEqualTo("native friendly")

    val attachment: FormField = decodedForm.get("attachment").get
    assertThat(attachment.filename).isEqualTo(Some("note.txt"))
    assertThat(attachment.contentType).isEqualTo(MediaType.text.plain)
    assertThat(unsafeRun(attachment.asChunk)).isEqualTo(binaryData)
  }

  @Test
  def urlsPathsAndQueryParamsRoundTripAndResolve(): Unit = {
    val queryParams: QueryParams = QueryParams("tag" -> Chunk("zio http", "native"), "empty" -> Chunk.empty)
    assertThat(queryParams.normalize.map.contains("empty")).isFalse()
    assertThat(QueryParams.decode(queryParams.encode).getAll("tag")).isEqualTo(Chunk("zio http", "native"))

    val path: Path = (Path.root / "api" / "v1" / "items" / "").dropTrailingSlash
    assertThat(path.encode).isEqualTo("/api/v1/items")
    assertThat((Path("/api") ++ Path("v1/./items/../items")).removeDotSegments.encode).isEqualTo("/api/v1/items")
    assertThat(Path("/api/v1/items").unnest(Path("/api")).encode).isEqualTo("v1/items")

    val decodedUrl: URL = URL.decode("https://example.com:8443/api/v1/items?tag=zio%20http&tag=native#details").toOption.get
    assertThat(decodedUrl.isAbsolute).isTrue()
    assertThat(decodedUrl.scheme.map(_.encode)).isEqualTo(Some("https"))
    assertThat(decodedUrl.hostPort).isEqualTo(Some("example.com:8443"))
    assertThat(decodedUrl.queryParams.getAll("tag")).isEqualTo(Chunk("zio http", "native"))
    assertThat(decodedUrl.fragment.map(_.decoded)).isEqualTo(Some("details"))

    val baseUrl: URL = URL.decode("https://example.com/docs/reference/index.html?lang=en").toOption.get
    val resolvedUrl: URL = baseUrl.resolve(URL.decode("../guide/start.html?lang=scala").toOption.get).toOption.get
    assertThat(resolvedUrl.encode).isEqualTo("https://example.com/docs/guide/start.html?lang=scala")
  }

  @Test
  def requestsHeadersCookiesAndResponsesCompose(): Unit = {
    val request: Request = Request
      .post("/submit", Body.fromString("payload"))
      .addCookie(Cookie.Request("session", "abc"))
      .addCookies(Cookie.Request("theme", "dark"))
      .addHeader(Header.Accept(MediaType.application.json))
      .addQueryParam("debug", "true")

    assertThat(request.method).isEqualTo(Method.POST)
    assertThat(request.cookie("session").map(_.content)).isEqualTo(Some("abc"))
    assertThat(request.cookies.map(_.name)).isEqualTo(Chunk("session", "theme"))
    assertThat(request.header(Header.Accept).map(_.mimeTypes.head.mediaType)).isEqualTo(Some(MediaType.application.json))
    assertThat(request.queryParam("debug")).isEqualTo(Some("true"))
    assertThat(unsafeRun(request.body.asString)).isEqualTo("payload")

    val responseCookie: Cookie.Response = Cookie
      .Request("session", "abc")
      .toResponse(path = Some(Path.root), isHttpOnly = true, sameSite = Some(Cookie.SameSite.Strict))
    val response: Response = Response
      .json("{\"ok\":true}")
      .addCookie(responseCookie)
      .patch(Response.Patch.status(Status.Created) ++ Response.Patch.addHeader("x-test", "yes"))

    assertThat(response.status).isEqualTo(Status.Created)
    assertThat(response.header(Header.ContentType).map(_.mediaType)).isEqualTo(Some(MediaType.application.json))
    assertThat(response.rawHeader("x-test")).isEqualTo(Some("yes"))
    val setCookies: Chunk[Header.SetCookie] = response.headers(Header.SetCookie)
    assertThat(setCookies.size).isEqualTo(1)
    assertThat(setCookies.head.value.isHttpOnly).isTrue()
    assertThat(setCookies.head.value.sameSite).isEqualTo(Some(Cookie.SameSite.Strict))
    assertThat(unsafeRun(response.body.asString)).isEqualTo("{\"ok\":true}")

    val updatedHeaders: Headers = response.headers.removeHeader("x-test").addHeader("x-replacement", "present")
    assertThat(updatedHeaders.contains("x-test")).isFalse()
    assertThat(updatedHeaders.get("x-replacement")).isEqualTo(Some("present"))
  }

  @Test
  def routesDispatchPathParametersBodiesAndErrors(): Unit = {
    val routes: Routes[Any, Nothing] = Routes(
      Method.GET / "users" / int("id") -> handler { (id: Int, request: Request) =>
        val tab: String = request.queryParamOrElse("tab", "summary")
        Response.text(s"user=$id tab=$tab")
      },
      Method.POST / "echo" -> handler { (request: Request) =>
        request.body.asString.map(body => Response.text(body.reverse))
      },
      Method.GET / "boom" -> handler { (_: Request) =>
        ZIO.fail(new IllegalArgumentException("bad input"))
      },
    ).handleError { error =>
      Response.badRequest(error.getMessage)
    }

    assertThat(routes.isDefinedAt(Request.get("/users/42"))).isTrue()
    assertThat(routes.isDefinedAt(Request.get("/missing"))).isFalse()

    val userResponse: Response = unsafeRun(
      ZIO.scoped(routes.runZIO(Request.get(URL.decode("/users/42?tab=settings").toOption.get))),
    )
    assertThat(userResponse.status).isEqualTo(Status.Ok)
    assertThat(unsafeRun(userResponse.body.asString)).isEqualTo("user=42 tab=settings")

    val echoResponse: Response = unsafeRun(ZIO.scoped(routes.runZIO(Request.post("/echo", Body.fromString("abc")))))
    assertThat(echoResponse.status).isEqualTo(Status.Ok)
    assertThat(unsafeRun(echoResponse.body.asString)).isEqualTo("cba")

    val handledErrorResponse: Response = unsafeRun(ZIO.scoped(routes.runZIO(Request.get("/boom"))))
    assertThat(handledErrorResponse.status).isEqualTo(Status.BadRequest)
    assertThat(unsafeRun(handledErrorResponse.body.asString)).contains("bad input")
  }

  @Test
  def webSocketFramesModelTextBinaryAndControlMessages(): Unit = {
    val textFrame: WebSocketFrame = WebSocketFrame.text("hello socket")
    assertThat(textFrame.isFinal).isTrue()
    textFrame match {
      case frame: WebSocketFrame.Text =>
        assertThat(frame.text).isEqualTo("hello socket")
        assertThat(frame.copy("updated").text).isEqualTo("updated")
      case other => throw new AssertionError(s"Expected text frame, got $other")
    }

    val bytes: Chunk[Byte] = Chunk.fromArray(Array[Byte](1, 2, 3))
    val binaryFrame: WebSocketFrame = WebSocketFrame.binary(bytes)
    assertThat(binaryFrame.isFinal).isTrue()
    binaryFrame match {
      case frame: WebSocketFrame.Binary => assertThat(frame.bytes).isEqualTo(bytes)
      case other                        => throw new AssertionError(s"Expected binary frame, got $other")
    }

    val closeFrame: WebSocketFrame = WebSocketFrame.close(1000, Some("normal shutdown"))
    assertThat(closeFrame.isFinal).isTrue()
    closeFrame match {
      case frame: WebSocketFrame.Close =>
        assertThat(frame.status).isEqualTo(1000)
        assertThat(frame.reason).isEqualTo(Some("normal shutdown"))
      case other => throw new AssertionError(s"Expected close frame, got $other")
    }

    assertThat(WebSocketFrame.ping).isSameAs(WebSocketFrame.Ping)
    assertThat(WebSocketFrame.pong).isSameAs(WebSocketFrame.Pong)
    assertThat(WebSocketFrame.continuation(Chunk.fromArray(Array[Byte](4, 5))).isFinal).isTrue()
  }

  @Test
  def handlersAndResponseConstructorsTransformRequests(): Unit = {
    val routeRequestThroughHandler: Handler[Any, Nothing, Request, Response] = Handler
      .fromFunction[Request] { request =>
        Response.text(request.path.encode).addHeader("x-path", request.path.encode)
      }
      .path(Path("/rewritten"))
      .status(Status.Accepted)

    val handlerResponse: Response = unsafeRun(ZIO.scoped(routeRequestThroughHandler.runZIO(Request.get("/original"))))
    assertThat(handlerResponse.status).isEqualTo(Status.Accepted)
    assertThat(handlerResponse.rawHeader("x-path")).isEqualTo(Some("/rewritten"))
    assertThat(unsafeRun(handlerResponse.body.asString)).isEqualTo("/rewritten")

    val redirect: Response = Response.redirect(URL.decode("/new-location").toOption.get, isPermanent = true)
    assertThat(redirect.status).isEqualTo(Status.PermanentRedirect)
    assertThat(redirect.header(Header.Location).map(_.url.encode)).isEqualTo(Some("/new-location"))

    val throwableResponse: Response = Response.fromThrowable(new java.io.FileNotFoundException("missing.txt"))
    assertThat(throwableResponse.status).isEqualTo(Status.NotFound)
    assertThat(Status.fromInt(201)).isEqualTo(Status.Created)
    assertThat(Status.fromString("404")).isEqualTo(Some(Status.NotFound))
    assertThat(Method.fromString("custom-method")).isEqualTo(Method.CUSTOM("custom-method"))
  }

  private def unsafeRun[A](effect: ZIO[Any, Any, A]): A = {
    val bounded: ZIO[Any, Any, A] = effect.timeoutFail(new RuntimeException("ZIO effect timed out"))(Duration.fromSeconds(5))
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(bounded).getOrThrowFiberFailure()
    }
  }
}
