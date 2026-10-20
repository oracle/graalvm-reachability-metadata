/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_zio_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import sttp.apispec.openapi.{Info, OpenAPI}
import sttp.apispec.openapi.circe.yaml.*
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.{Schema, Validator}
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.json.zio.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.*
import zio.http.Server
import zio.json.{DecoderOps, DeriveJsonCodec, JsonCodec}
import zio.{RIO, Ref, Runtime, Task, UIO, Unsafe, ZIO, ZLayer}

/**
 * OpenAPI documentation and the bundled Swagger UI generated from `ZServerEndpoint`s and served
 * next to them over the ZIO HTTP interpreter.
 *
 * A recipe box application describes its endpoints with names, tags, descriptions, examples,
 * validated query parameters and bearer security, implements them with `zServerLogic` against a
 * `Recipes` service from the ZIO environment, and hands the same server endpoints to
 * `SwaggerInterpreter`, which adds documentation endpoints in the same `RIO[Recipes, *]` effect.
 * The test reads the OpenAPI model produced by `OpenAPIDocsInterpreter`, then calls the running
 * server over HTTP and checks that the API still answers, that `/docs/docs.yaml` carries the
 * documented operations, that the Swagger UI initializer points at that YAML, that the bare docs
 * path redirects to the UI index page and that the UI assets are served from the bundle.
 */
class RecipeBoxZioSwaggerDocsTest {
  import RecipeBoxZioSwaggerDocsTest.*

  @Test
  def swaggerUiAndOpenApiYamlAreServedNextToTheApi(): Unit = {
    val model: OpenAPI = OpenAPIDocsInterpreter().serverEndpointsToOpenAPI(apiEndpoints, info)
    assertEquals("Recipe box", model.info.title)
    assertEquals(Set("/recipes", "/recipes/{id}"), model.paths.pathItems.keySet)
    val list = model.paths.pathItems("/recipes").get.get
    assertEquals(List("recipes"), list.tags)
    assertEquals(Some("List recipes, optionally filtered by tag"), list.description)
    assertEquals(List("tag", "limit"), list.parameters.flatMap(_.toOption).map(_.name))
    val add = model.paths.pathItems("/recipes").post.get
    assertEquals(Set("bearerAuth"), add.security.flatMap(_.keySet).toSet)
    assertTrue(model.paths.pathItems("/recipes/{id}").get.get.deprecated.contains(true))
    assertTrue(model.components.exists(_.schemas.contains("Recipe")), s"components: ${model.components}")

    val program: ZIO[Server & Recipes, Throwable, Observed] = for {
      port <- Server.install(ZioHttpInterpreter().toHttp(apiEndpoints ++ docsEndpoints))
      observed <- ZIO.scoped(HttpClientZioBackend.scoped().flatMap(backend => exercise(port, backend)))
    } yield observed

    val observed: Observed = Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe
        .run(program.provide(Server.defaultWithPort(0), Recipes.layer))
        .getOrThrowFiberFailure()
    }

    assertEquals(StatusCode.Created, observed.added.code)
    assertEquals(Recipe(3L, "shakshuka", List("breakfast", "eggs")), decode[Recipe](observed.added.body))
    assertEquals(StatusCode.Ok, observed.listed.code)
    assertEquals(List("omelette", "shakshuka"), decode[List[Recipe]](observed.listed.body).map(_.title))
    assertEquals(StatusCode.BadRequest, observed.badLimit.code)

    assertEquals(StatusCode.Ok, observed.yaml.code)
    assertEquals(model.toYaml, observed.yaml.body)
    List(
      "openapi: 3.1.0",
      "title: Recipe box",
      "version: 2.4.0",
      "/recipes/{id}:",
      "summary: Add a recipe",
      "- recipes",
      "bearerAuth:",
      "scheme: bearer",
      "shakshuka"
    ).foreach(fragment =>
      assertTrue(observed.yaml.body.contains(fragment), s"docs.yaml lacks '$fragment':\n${observed.yaml.body}")
    )
    assertTrue(!observed.yaml.body.contains("/docs"), "documentation endpoints must not document themselves")

    assertEquals(StatusCode.Ok, observed.initializer.code)
    assertTrue(observed.initializer.body.contains("./docs.yaml"), observed.initializer.body)

    assertTrue(observed.redirect.code.isRedirect, s"bare docs path answered ${observed.redirect.code}")
    assertTrue(
      observed.redirect.location.exists(_.endsWith("/docs/")),
      s"bare docs path redirected to ${observed.redirect.location}"
    )

    assertEquals(StatusCode.Ok, observed.index.code)
    assertTrue(observed.index.body.contains("<title>Swagger UI</title>"), observed.index.body)
    assertEquals(StatusCode.Ok, observed.bundle.code)
    assertTrue(observed.bundle.body.contains("SwaggerUIBundle"), "swagger-ui-bundle.js lacks its export")
  }

  private def exercise(port: Int, backend: Backend[Task]): Task[Observed] = {
    val base: String = s"http://localhost:$port"
    def send(request: Request[Either[String, String]]): Task[Reply] =
      request.send(backend).map(r => Reply(r.code, r.body.fold(identity, identity), r.header(HeaderNames.Location)))
    for {
      added <- send(
        basicRequest.post(uri"$base/recipes").auth.bearer("chef").contentType("application/json")
          .body("""{"title":"shakshuka","tags":["breakfast","eggs"]}""")
      )
      listed <- send(basicRequest.get(uri"$base/recipes?tag=eggs&limit=5"))
      badLimit <- send(basicRequest.get(uri"$base/recipes?limit=500"))
      yaml <- send(basicRequest.get(uri"$base/docs/docs.yaml"))
      initializer <- send(basicRequest.get(uri"$base/docs/swagger-initializer.js"))
      redirect <- send(basicRequest.get(uri"$base/docs").followRedirects(false))
      index <- send(basicRequest.get(uri"$base/docs/index.html"))
      bundle <- send(basicRequest.get(uri"$base/docs/swagger-ui-bundle.js"))
    } yield Observed(added, listed, badLimit, yaml, initializer, redirect, index, bundle)
  }

  private def decode[A: JsonCodec](body: String): A =
    body.fromJson[A].fold(error => throw new AssertionError(s"$error in $body"), identity)
}

object RecipeBoxZioSwaggerDocsTest {
  final case class NewRecipe(title: String, tags: List[String]) derives Schema
  final case class Recipe(id: Long, title: String, tags: List[String]) derives Schema
  final case class NotFound(id: Long) derives Schema

  given JsonCodec[NewRecipe] = DeriveJsonCodec.gen[NewRecipe]
  given JsonCodec[Recipe] = DeriveJsonCodec.gen[Recipe]
  given JsonCodec[NotFound] = DeriveJsonCodec.gen[NotFound]

  final case class Reply(code: StatusCode, body: String, location: Option[String])

  final case class Observed(
      added: Reply,
      listed: Reply,
      badLimit: Reply,
      yaml: Reply,
      initializer: Reply,
      redirect: Reply,
      index: Reply,
      bundle: Reply
  )

  /** Stores recipes; required by every API endpoint. */
  trait Recipes {
    def add(recipe: NewRecipe): UIO[Recipe]
    def find(id: Long): UIO[Option[Recipe]]
    def byTag(tag: Option[String], limit: Int): UIO[List[Recipe]]
  }

  object Recipes {
    val layer: ZLayer[Any, Nothing, Recipes] = ZLayer.fromZIO(
      Ref.make(Vector(Recipe(1L, "pancakes", List("breakfast")), Recipe(2L, "omelette", List("breakfast", "eggs"))))
        .map { recipes =>
          new Recipes {
            def add(recipe: NewRecipe): UIO[Recipe] =
              recipes.modify(all => {
                val created: Recipe = Recipe(all.size + 1L, recipe.title, recipe.tags)
                (created, all :+ created)
              })
            def find(id: Long): UIO[Option[Recipe]] = recipes.get.map(_.find(_.id == id))
            def byTag(tag: Option[String], limit: Int): UIO[List[Recipe]] =
              recipes.get.map(_.filter(r => tag.forall(r.tags.contains)).take(limit).toList)
          }
        }
    )
  }

  val info: Info = Info("Recipe box", "2.4.0", description = Some("Personal recipe collection"))

  private val recipes = endpoint.tag("recipes").in("recipes")

  private val listRecipes: ZServerEndpoint[Recipes, Any] = recipes.get
    .name("listRecipes")
    .description("List recipes, optionally filtered by tag")
    .in(query[Option[String]]("tag").description("Only recipes carrying this tag"))
    .in(query[Int]("limit").default(20).validate(Validator.inRange(1, 100)))
    .out(jsonBody[List[Recipe]])
    .zServerLogic[Recipes]((tag, limit) => ZIO.serviceWithZIO[Recipes](_.byTag(tag, limit)))

  private val getRecipe: ZServerEndpoint[Recipes, Any] = recipes.get
    .in(path[Long]("id").description("Recipe identifier"))
    .deprecated()
    .errorOut(statusCode(StatusCode.NotFound).and(jsonBody[NotFound]))
    .out(jsonBody[Recipe])
    .zServerLogic[Recipes](id => ZIO.serviceWithZIO[Recipes](_.find(id)).someOrFail(NotFound(id)))

  private val addRecipe: ZServerEndpoint[Recipes, Any] = recipes.post
    .summary("Add a recipe")
    .securityIn(auth.bearer[String]().securitySchemeName("bearerAuth"))
    .in(jsonBody[NewRecipe].example(NewRecipe("shakshuka", List("breakfast", "eggs"))))
    .out(statusCode(StatusCode.Created).and(jsonBody[Recipe]))
    .zServerSecurityLogic[Recipes, String](token => ZIO.succeed(token))
    .serverLogic[Recipes](_ => recipe => ZIO.serviceWithZIO[Recipes](_.add(recipe)))

  val apiEndpoints: List[ZServerEndpoint[Recipes, Any]] = List(listRecipes, getRecipe, addRecipe)

  val docsEndpoints: List[ZServerEndpoint[Recipes, Any]] =
    SwaggerInterpreter().fromServerEndpoints[[A] =>> RIO[Recipes, A]](apiEndpoints, info)
}
