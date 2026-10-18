/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import com.sun.net.httpserver.HttpServer
import io.circe.Codec as CirceCodec
import io.circe.parser.decode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.apispec.openapi.OpenAPI
import sttp.apispec.openapi.Server
import sttp.client3.HttpClientSyncBackend
import sttp.client3.Identity
import sttp.client3.Response
import sttp.client3.SttpBackend
import sttp.client3.basicRequest
import sttp.model.MediaType
import sttp.model.StatusCode
import sttp.model.Uri
import sttp.model.Uri.UriContext
import sttp.tapir.*
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.json.circe.*
import sttp.tapir.server.jdkhttp.JdkHttpServer
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter

/**
 * Swagger UI served next to circe JSON endpoints: the Swagger UI bundle publishes the OpenAPI
 * document of `jsonBody` endpoints (schemas, circe-encoded examples) as YAML from a live JDK HTTP
 * server together with the Swagger UI assets, and the documented API answers the JSON calls.
 *
 * A library catalog exposes its book API and, under `/docs`, the interactive documentation a
 * developer opens in a browser. The browser follows the redirect to the UI, loads the page and the
 * initializer script pointing at the catalog YAML, reads the schemas and examples there, and then
 * sends the same JSON requests the "Try it out" button would.
 */
class LibraryCatalogSwaggerUiTest {
  import LibraryCatalogSwaggerUiTest.*

  private var server: HttpServer = null
  private var backend: SttpBackend[Identity, Any] = null
  private var baseUri: Uri = null
  private val catalog: Catalog = new Catalog

  @BeforeEach
  def startServer(): Unit = {
    val docs = SwaggerInterpreter(
      customiseDocsModel = (openApi: OpenAPI) => openApi.addServer(Server("http://catalog.example.org")),
      swaggerUIOptions = SwaggerUIOptions.default
        .yamlName("catalog.yaml")
        .copy(initializerOptions = Some(Map("docExpansion" -> "\"full\"")))
    ).fromServerEndpoints(catalog.serverEndpoints, "Library Catalog", "2.1.0")
    server = JdkHttpServer()
      .host("localhost")
      .port(0)
      .addEndpoints(catalog.serverEndpoints)
      .addEndpoints(docs)
      .start()
    backend = HttpClientSyncBackend()
    baseUri = uri"http://localhost:${server.getAddress.getPort}"
  }

  @AfterEach
  def stopServer(): Unit = {
    if (backend != null) backend.close()
    if (server != null) server.stop(0)
  }

  @Test
  def redirectsToTheSwaggerUiPageAndItsInitializer(): Unit = {
    val redirect: Response[Either[String, String]] = basicRequest
      .get(uri"$baseUri/docs")
      .followRedirects(false)
      .send(backend)
    assertEquals(StatusCode.PermanentRedirect, redirect.code)
    assertEquals(Some("./docs/"), redirect.header("Location"))

    val page: Response[Either[String, String]] = basicRequest.get(uri"$baseUri/docs/index.html").send(backend)
    assertEquals(StatusCode.Ok, page.code)
    assertTrue(page.header("Content-Type").exists(_.startsWith("text/html")))
    val html: String = page.body.fold(identity, identity)
    assertTrue(html.contains("<div id=\"swagger-ui\"></div>"), html)
    assertTrue(html.contains("swagger-initializer.js"), html)

    val initializer: Response[Either[String, String]] =
      basicRequest.get(uri"$baseUri/docs/swagger-initializer.js").send(backend)
    assertEquals(StatusCode.Ok, initializer.code)
    val script: String = initializer.body.fold(identity, identity)
    assertTrue(script.contains("url: \"./catalog.yaml\""), script)
    assertTrue(script.contains("docExpansion: \"full\","), script)
    assertTrue(script.contains("showExtensions: false,"), script)
    assertTrue(!script.contains("petstore.swagger.io"), script)

    val bundle: Response[Either[String, String]] =
      basicRequest.get(uri"$baseUri/docs/swagger-ui-bundle.js").send(backend)
    assertEquals(StatusCode.Ok, bundle.code)
    assertTrue(bundle.body.fold(identity, identity).contains("SwaggerUIBundle"))
  }

  @Test
  def publishesTheJsonSchemasAndExamplesOfTheCatalogApi(): Unit = {
    val response: Response[Either[String, String]] = basicRequest
      .get(uri"$baseUri/docs/catalog.yaml")
      .header("Accept", "application/yaml")
      .send(backend)
    assertEquals(StatusCode.Ok, response.code)
    val yaml: String = response.body.fold(identity, identity)

    assertTrue(yaml.startsWith("openapi: 3.1.0"), yaml)
    assertTrue(yaml.contains("title: Library Catalog"), yaml)
    assertTrue(yaml.contains("version: 2.1.0"), yaml)
    assertTrue(yaml.contains("- url: http://catalog.example.org"), yaml)
    assertTrue(yaml.contains("  /books:\n"), yaml)
    assertTrue(yaml.contains("  /books/{isbn}:\n"), yaml)
    assertTrue(yaml.contains("operationId: postBooks"), yaml)
    assertTrue(yaml.contains("operationId: getBooksIsbn"), yaml)
    assertTrue(yaml.contains("application/json:"), yaml)
    assertTrue(yaml.contains("$ref: '#/components/schemas/NewBook'"), yaml)
    assertTrue(yaml.contains("$ref: '#/components/schemas/Book'"), yaml)
    assertTrue(yaml.contains("$ref: '#/components/schemas/Problem'"), yaml)
    assertTrue(yaml.contains("    NewBook:\n"), yaml)
    assertTrue(yaml.contains("    Book:\n"), yaml)
    assertTrue(yaml.contains("    Problem:\n"), yaml)
    assertTrue(yaml.contains("title: Dune"), yaml)
    assertTrue(yaml.contains("- Frank Herbert"), yaml)
    assertTrue(yaml.contains("isbn: 978-0441013593"), yaml)
    assertTrue(yaml.contains("'404':"), yaml)
    assertTrue(yaml.contains("'201':"), yaml)
  }

  @Test
  def answersTheDocumentedJsonCallsLikeTryItOut(): Unit = {
    val created: Response[Either[String, String]] = basicRequest
      .post(uri"$baseUri/books")
      .header("Accept", "application/json")
      .contentType(MediaType.ApplicationJson)
      .body("""{"title":"Dune","authors":["Frank Herbert"],"year":1965}""")
      .send(backend)
    assertEquals(StatusCode.Created, created.code)
    assertTrue(created.header("Content-Type").exists(_.startsWith("application/json")))
    assertEquals(
      Right(Book("978-0000000001", "Dune", List("Frank Herbert"), 1965)),
      decode[Book](created.body.fold(identity, identity))
    )

    val client: SttpClientInterpreter = SttpClientInterpreter()
    val fetched: Either[Problem, Book] = client
      .toRequestThrowDecodeFailures(findBook, Some(baseUri))
      .apply("978-0000000001")
      .send(backend)
      .body
    assertEquals(Right(Book("978-0000000001", "Dune", List("Frank Herbert"), 1965)), fetched)

    val missing: Response[Either[Problem, Book]] = client
      .toRequestThrowDecodeFailures(findBook, Some(baseUri))
      .apply("978-9999999999")
      .send(backend)
    assertEquals(StatusCode.NotFound, missing.code)
    assertEquals(Left(Problem("unknown-book", "No book with ISBN 978-9999999999")), missing.body)

    val second: Either[Unit, Book] = client
      .toRequestThrowDecodeFailures(addBook, Some(baseUri))
      .apply(NewBook("Neuromancer", List("William Gibson"), 1984))
      .send(backend)
      .body
    assertEquals(Right(Book("978-0000000002", "Neuromancer", List("William Gibson"), 1984)), second)
    assertEquals(List("Dune", "Neuromancer"), catalog.titles)
  }
}

object LibraryCatalogSwaggerUiTest {

  final case class NewBook(title: String, authors: List[String], year: Int) derives CirceCodec.AsObject, Schema

  final case class Book(isbn: String, title: String, authors: List[String], year: Int)
      derives CirceCodec.AsObject, Schema

  final case class Problem(code: String, detail: String) derives CirceCodec.AsObject, Schema

  val addBook: Endpoint[Unit, NewBook, Unit, Book, Any] = endpoint.post
    .in("books")
    .in(jsonBody[NewBook].example(NewBook("Dune", List("Frank Herbert"), 1965)))
    .out(statusCode(StatusCode.Created))
    .out(jsonBody[Book])

  val findBook: Endpoint[Unit, String, Problem, Book, Any] = endpoint.get
    .in("books" / path[String]("isbn"))
    .errorOut(statusCode(StatusCode.NotFound).and(jsonBody[Problem]))
    .out(jsonBody[Book].example(Book("978-0441013593", "Dune", List("Frank Herbert"), 1965)))

  /** In-memory books keyed by ISBN, handing out sequential ISBNs. */
  final class Catalog {
    private var books: Vector[Book] = Vector.empty

    def titles: List[String] = synchronized(books.map(_.title).toList)

    val serverEndpoints = List(
      addBook.handleSuccess { newBook =>
        synchronized {
          val isbn: String = f"978-${books.size + 1}%010d"
          val book: Book = Book(isbn, newBook.title, newBook.authors, newBook.year)
          books = books :+ book
          book
        }
      },
      findBook.handle { isbn =>
        synchronized(books.find(_.isbn == isbn))
          .toRight(Problem("unknown-book", s"No book with ISBN $isbn"))
      }
    )
  }
}
