/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_dsl_3

import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.*
import org.http4s.CacheDirective
import org.http4s.EntityTag
import org.http4s.HttpDate
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.ETag
import org.http4s.headers.`Cache-Control`
import org.http4s.headers.`If-Match`
import org.http4s.headers.`If-None-Match`
import org.http4s.headers.`Last-Modified`
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.typelevel.ci.*
import scala.concurrent.duration.*

/**
 * Conditional requests and optimistic concurrency with entity tags and the DSL precondition statuses.
 *
 * A wiki serves each page with an `ETag`, a `Last-Modified` date and a `Cache-Control` policy.
 * A reader revalidating with a matching `If-None-Match` gets `304 Not Modified` without a body,
 * while a stale tag yields the current page. Editors must send `If-Match`: an edit without it is
 * refused with `428 Precondition Required`, an edit against an outdated tag with
 * `412 Precondition Failed`, and an edit against the current tag answers `204 No Content` with the
 * new tag. Creating a page with `If-None-Match: *` succeeds once with `201 Created` and fails with
 * `412` when the page already exists. The wiki runs on an Ember server and is called by an Ember
 * client.
 */
class WikiConditionalRequestsTest {
  import WikiConditionalRequestsTest.*

  @Test
  def wikiHonoursEntityTagPreconditions(): Unit = {
    val outcome: Observed = serveAndCall { (client: Client[IO], base: String) =>
      val home: Uri = Uri.unsafeFromString(s"$base/pages/home")
      val faq: Uri = Uri.unsafeFromString(s"$base/pages/faq")

      for {
        first <- fetch(client, Request[IO](Method.GET, home))
        revalidated <- fetch(client, Request[IO](Method.GET, home).putHeaders(`If-None-Match`(EntityTag("home-1"))))
        weakRevalidated <- fetch(
          client,
          Request[IO](Method.GET, home).putHeaders(`If-None-Match`(EntityTag("home-1", EntityTag.Weak)))
        )
        staleCache <- fetch(client, Request[IO](Method.GET, home).putHeaders(`If-None-Match`(EntityTag("home-0"))))
        blindEdit <- fetch(client, Request[IO](Method.PUT, home).withEntity("Overwritten"))
        lostUpdate <- fetch(
          client,
          Request[IO](Method.PUT, home).withEntity("Stale edit").putHeaders(`If-Match`(EntityTag("home-0")))
        )
        edit <- fetch(
          client,
          Request[IO](Method.PUT, home)
            .withEntity("Welcome to the team wiki")
            .putHeaders(`If-Match`(EntityTag("home-0"), EntityTag("home-1")))
        )
        afterEdit <- fetch(client, Request[IO](Method.GET, home).putHeaders(`If-None-Match`(EntityTag("home-1"))))
        created <- fetch(client, Request[IO](Method.PUT, faq).withEntity("Ask anything").putHeaders(`If-None-Match`.`*`))
        duplicate <- fetch(client, Request[IO](Method.PUT, faq).withEntity("Again").putHeaders(`If-None-Match`.`*`))
        faqPage <- fetch(client, Request[IO](Method.GET, faq))
        missing <- fetch(
          client,
          Request[IO](Method.PUT, Uri.unsafeFromString(s"$base/pages/rules"))
            .withEntity("No rules")
            .putHeaders(`If-Match`(EntityTag("rules-1")))
        )
      } yield Observed(
        first,
        revalidated,
        weakRevalidated,
        staleCache,
        blindEdit,
        lostUpdate,
        edit,
        afterEdit,
        created,
        duplicate,
        faqPage,
        missing
      )
    }

    assertEquals(
      Seen(Status.Ok, Some(EntityTag("home-1")), Some(PublishedAt), Some("max-age=60"), "Welcome"),
      outcome.first
    )
    assertEquals(Seen(Status.NotModified, Some(EntityTag("home-1")), None, Some("max-age=60"), ""), outcome.revalidated)
    assertEquals(Status.NotModified, outcome.weakRevalidated.status)
    assertEquals(
      Seen(Status.Ok, Some(EntityTag("home-1")), Some(PublishedAt), Some("max-age=60"), "Welcome"),
      outcome.staleCache
    )
    assertEquals(Seen(Status.PreconditionRequired, None, None, None, "edits must send If-Match"), outcome.blindEdit)
    assertEquals(
      Seen(Status.PreconditionFailed, Some(EntityTag("home-1")), None, None, "home changed since home-0"),
      outcome.lostUpdate
    )
    assertEquals(Seen(Status.NoContent, Some(EntityTag("home-2")), None, None, ""), outcome.edit)
    assertEquals(
      Seen(Status.Ok, Some(EntityTag("home-2")), Some(editedAt(2)), Some("max-age=60"), "Welcome to the team wiki"),
      outcome.afterEdit
    )
    assertEquals(Seen(Status.Created, Some(EntityTag("faq-1")), None, None, "created faq"), outcome.created)
    assertEquals(
      Seen(Status.PreconditionFailed, Some(EntityTag("faq-1")), None, None, "faq already exists"),
      outcome.duplicate
    )
    assertEquals(
      Seen(Status.Ok, Some(EntityTag("faq-1")), Some(editedAt(1)), Some("max-age=60"), "Ask anything"),
      outcome.faqPage
    )
    assertEquals(Seen(Status.PreconditionFailed, None, None, None, "rules does not exist"), outcome.missing)
  }
}

object WikiConditionalRequestsTest {

  /** The status, validators, caching policy and body one response carried. */
  final case class Seen(
      status: Status,
      etag: Option[EntityTag],
      lastModified: Option[HttpDate],
      cacheControl: Option[String],
      body: String
  )

  /** What the client saw for each request. */
  final case class Observed(
      first: Seen,
      revalidated: Seen,
      weakRevalidated: Seen,
      staleCache: Seen,
      blindEdit: Seen,
      lostUpdate: Seen,
      edit: Seen,
      afterEdit: Seen,
      created: Seen,
      duplicate: Seen,
      faqPage: Seen,
      missing: Seen
  )

  /** One stored page: its text, its revision number and when that revision was written. */
  final case class Page(text: String, revision: Int, modified: HttpDate) {
    def tag(name: String): EntityTag = EntityTag(s"$name-$revision")
  }

  val PublishedAt: HttpDate = HttpDate.unsafeFromEpochSecond(1_790_000_000L)

  /** The wiki stamps revisions from a fixed clock so the scenario observes stable dates. */
  def editedAt(revision: Int): HttpDate = HttpDate.unsafeFromEpochSecond(1_790_000_000L + revision * 3_600L)

  val Caching: `Cache-Control` = `Cache-Control`(CacheDirective.`max-age`(60.seconds))

  /** True when any tag in `header` names the same revision as `current`, ignoring weakness. */
  def weaklyMatches(header: `If-None-Match`, current: EntityTag): Boolean =
    header.tags.forall(_.exists(_.tag == current.tag))

  /** The wiki API as an application would mount it at the server root. */
  def wikiRoutes(pages: Ref[IO, Map[String, Page]]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case request @ GET -> Root / "pages" / name =>
      pages.get.map(_.get(name)).flatMap {
        case None => NotFound(s"no page $name")
        case Some(page) =>
          request.headers.get[`If-None-Match`] match {
            case Some(condition) if weaklyMatches(condition, page.tag(name)) =>
              NotModified.headers(ETag(page.tag(name)), Caching)
            case _ =>
              Ok(page.text, ETag(page.tag(name)), `Last-Modified`(page.modified), Caching)
          }
      }

    case request @ PUT -> Root / "pages" / name =>
      request.as[String].flatMap { text =>
        (request.headers.get[`If-Match`], request.headers.get[`If-None-Match`]) match {
          case (_, Some(`If-None-Match`(None))) => create(pages, name, text)
          case (Some(condition), _) => update(pages, name, text, condition)
          case (None, _) => PreconditionRequired("edits must send If-Match")
        }
      }
  }

  /** Stores a new page unless one already exists under `name`. */
  def create(pages: Ref[IO, Map[String, Page]], name: String, text: String): IO[Response[IO]] =
    pages
      .modify { current =>
        current.get(name) match {
          case Some(existing) => (current, Left(existing))
          case None =>
            val page: Page = Page(text, 1, editedAt(1))
            (current.updated(name, page), Right(page))
        }
      }
      .flatMap {
        case Left(existing) => PreconditionFailed(s"$name already exists", ETag(existing.tag(name)))
        case Right(page) => Created(s"created $name", ETag(page.tag(name)))
      }

  /** Replaces a page only when the editor saw its current revision. */
  def update(pages: Ref[IO, Map[String, Page]], name: String, text: String, condition: `If-Match`): IO[Response[IO]] =
    pages
      .modify { current =>
        current.get(name) match {
          case None => (current, Left(None))
          case Some(page) if condition.tags.forall(_.exists(_ == page.tag(name))) =>
            val next: Page = Page(text, page.revision + 1, editedAt(page.revision + 1))
            (current.updated(name, next), Right(next))
          case Some(page) => (current, Left(Some(page)))
        }
      }
      .flatMap {
        case Right(next) => NoContent.headers(ETag(next.tag(name)))
        case Left(Some(page)) =>
          val sent: String = condition.tags.map(_.head.tag).getOrElse("*")
          PreconditionFailed(s"$name changed since $sent", ETag(page.tag(name)))
        case Left(None) => PreconditionFailed(s"$name does not exist")
      }

  /** Starts the wiki on an ephemeral loopback port and runs `scenario` against it. */
  def serveAndCall[A](scenario: (Client[IO], String) => IO[A]): A = {
    val running: Resource[IO, (Client[IO], String)] = for {
      pages <- Resource.eval(Ref.of[IO, Map[String, Page]](Map("home" -> Page("Welcome", 1, PublishedAt))))
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(wikiRoutes(pages).orNotFound)
        .build
      client <- EmberClientBuilder.default[IO].build
    } yield (client, s"http://127.0.0.1:${server.address.getPort}")
    running.use((client, base) => scenario(client, base)).unsafeRunSync()
  }

  /** Sends `request` and reads the status, validators, caching policy and body. */
  def fetch(client: Client[IO], request: Request[IO]): IO[Seen] =
    client.run(request).use { response =>
      response.as[String].map { body =>
        Seen(
          response.status,
          response.headers.get[ETag].map(_.tag),
          response.headers.get[`Last-Modified`].map(_.date),
          response.headers.get(ci"Cache-Control").map(_.head.value),
          body
        )
      }
    }
}
