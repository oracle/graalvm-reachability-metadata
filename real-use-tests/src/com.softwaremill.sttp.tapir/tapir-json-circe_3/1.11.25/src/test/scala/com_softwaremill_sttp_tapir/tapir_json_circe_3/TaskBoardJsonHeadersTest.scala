/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import com.sun.net.httpserver.HttpServer
import io.circe.Codec as CirceCodec
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

import java.util.concurrent.atomic.AtomicLong

/**
 * JSON documents carried in HTTP headers: circe codecs adapted through `circeCodec` into header
 * codecs for a security input and for response headers, on a live JDK HTTP server called through
 * the sttp client interpreter.
 *
 * A multi-tenant task board sits behind a gateway that forwards the authenticated caller as a JSON
 * document in an `X-Caller` header. The board reads it as a security input, lists tasks with the
 * paging metadata in a JSON `X-Page-Info` response header and an optional JSON `X-Next-Page`
 * cursor, and refuses callers whose header is missing, malformed or lacks the required role.
 */
class TaskBoardJsonHeadersTest {
  import TaskBoardJsonHeadersTest.*

  private var server: HttpServer = null
  private var backend: SttpBackend[Identity, Any] = null
  private var baseUri: Uri = null
  private val board: TaskBoard = new TaskBoard

  private val acmeEditor: Caller = Caller("acme", "ada", List("tasks:read", "tasks:write"))
  private val acmeViewer: Caller = Caller("acme", "bob", List("tasks:read"))
  private val globexEditor: Caller = Caller("globex", "eve", List("tasks:read", "tasks:write"))

  @BeforeEach
  def startServer(): Unit = {
    server = JdkHttpServer()
      .host("localhost")
      .port(0)
      .addEndpoints(board.serverEndpoints)
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
  def pagesThroughTasksWithJsonPagingHeaders(): Unit = {
    val client: SttpClientInterpreter = SttpClientInterpreter()
    List("Write release notes", "Rotate keys", "Plan offsite").foreach { title =>
      create(acmeEditor, NewTask(title, priority = 2))
    }

    val first: (List[Task], PageInfo, Option[PageCursor]) = client
      .toSecureRequestThrowDecodeFailures(listTasks, Some(baseUri))
      .apply(acmeViewer)
      .apply(1)
      .send(backend)
      .body
      .fold(error => throw new AssertionError(s"unexpected error $error"), identity)

    assertEquals(List("Write release notes", "Rotate keys"), first._1.map(_.title))
    assertEquals(PageInfo(page = 1, pageSize = 2, total = 3), first._2)
    assertEquals(Some(PageCursor(page = 2, href = "/tasks?page=2")), first._3)

    val last: (List[Task], PageInfo, Option[PageCursor]) = client
      .toSecureRequestThrowDecodeFailures(listTasks, Some(baseUri))
      .apply(acmeViewer)
      .apply(first._3.get.page)
      .send(backend)
      .body
      .fold(error => throw new AssertionError(s"unexpected error $error"), identity)

    assertEquals(List("Plan offsite"), last._1.map(_.title))
    assertEquals(PageInfo(page = 2, pageSize = 2, total = 3), last._2)
    assertEquals(None, last._3)
  }

  @Test
  def createsTasksInTheTenantNamedByTheCallerHeader(): Unit = {
    val acmeTask: Task = create(acmeEditor, NewTask("Renew certificate", priority = 1))
    val globexTask: Task = create(globexEditor, NewTask("Order badges", priority = 3))

    assertEquals(Task(1L, "acme", "Renew certificate", 1, "ada"), acmeTask)
    assertEquals(Task(2L, "globex", "Order badges", 3, "eve"), globexTask)

    val client: SttpClientInterpreter = SttpClientInterpreter()
    val globexView: Either[(StatusCode, AuthError), (List[Task], PageInfo, Option[PageCursor])] = client
      .toSecureRequestThrowDecodeFailures(listTasks, Some(baseUri))
      .apply(globexEditor)
      .apply(1)
      .send(backend)
      .body

    assertEquals(Right((List(globexTask), PageInfo(1, 2, 1), None)), globexView)
  }

  @Test
  def writesTheHeadersAsCompactJsonOnTheWire(): Unit = {
    List("Fix login", "Update docs", "Triage inbox").foreach { title =>
      create(acmeEditor, NewTask(title, priority = 2))
    }

    val raw: Response[Either[String, String]] = basicRequest
      .get(uri"$baseUri/tasks?page=1")
      .header("X-Caller", """{"tenant":"acme","user":"bob","roles":["tasks:read"]}""")
      .send(backend)

    assertEquals(StatusCode.Ok, raw.code)
    assertTrue(raw.header("Content-Type").exists(_.startsWith("application/json")))
    assertEquals(Some("""{"page":1,"pageSize":2,"total":3}"""), raw.header("X-Page-Info"))
    assertEquals(Some("""{"page":2,"href":"/tasks?page=2"}"""), raw.header("X-Next-Page"))
    assertEquals(
      """[{"id":1,"tenant":"acme","title":"Fix login","priority":2,"createdBy":"ada"},"""
        + """{"id":2,"tenant":"acme","title":"Update docs","priority":2,"createdBy":"ada"}]""",
      raw.body.fold(identity, identity)
    )
  }

  @Test
  def refusesACallerWithoutTheRequiredRole(): Unit = {
    val client: SttpClientInterpreter = SttpClientInterpreter()

    val response: Response[Either[(StatusCode, AuthError), Task]] = client
      .toSecureRequestThrowDecodeFailures(createTask, Some(baseUri))
      .apply(acmeViewer)
      .apply(NewTask("Delete everything", priority = 1))
      .send(backend)

    assertEquals(StatusCode.Forbidden, response.code)
    assertEquals(
      Left((StatusCode.Forbidden, AuthError("missing-role", "bob needs tasks:write in acme"))),
      response.body
    )
    assertEquals(0, board.size)
  }

  @Test
  def rejectsAMissingOrMalformedCallerHeaderBeforeTheSecurityLogicRuns(): Unit = {
    val missing: Response[Either[String, String]] = basicRequest
      .post(uri"$baseUri/tasks")
      .contentType(MediaType.ApplicationJson)
      .body("""{"title":"Sneak in","priority":1}""")
      .send(backend)
    assertEquals(StatusCode.BadRequest, missing.code)

    val notJson: Response[Either[String, String]] = basicRequest
      .post(uri"$baseUri/tasks")
      .header("X-Caller", "ada@acme")
      .contentType(MediaType.ApplicationJson)
      .body("""{"title":"Sneak in","priority":1}""")
      .send(backend)
    assertEquals(StatusCode.BadRequest, notJson.code)

    val wrongShape: Response[Either[String, String]] = basicRequest
      .post(uri"$baseUri/tasks")
      .header("X-Caller", """{"user":"ada","roles":["tasks:write"]}""")
      .contentType(MediaType.ApplicationJson)
      .body("""{"title":"Sneak in","priority":1}""")
      .send(backend)
    assertEquals(StatusCode.BadRequest, wrongShape.code)

    assertEquals(0, board.securityChecks)
    assertEquals(0, board.size)
  }

  private def create(caller: Caller, task: NewTask): Task =
    SttpClientInterpreter()
      .toSecureRequestThrowDecodeFailures(createTask, Some(baseUri))
      .apply(caller)
      .apply(task)
      .send(backend)
      .body
      .fold(error => throw new AssertionError(s"unexpected error $error"), identity)
}

object TaskBoardJsonHeadersTest {

  final case class Caller(tenant: String, user: String, roles: List[String]) derives CirceCodec.AsObject, Schema

  final case class PageInfo(page: Int, pageSize: Int, total: Int) derives CirceCodec.AsObject, Schema

  final case class PageCursor(page: Int, href: String) derives CirceCodec.AsObject, Schema

  final case class NewTask(title: String, priority: Int) derives CirceCodec.AsObject, Schema

  final case class Task(id: Long, tenant: String, title: String, priority: Int, createdBy: String)
      derives CirceCodec.AsObject, Schema

  final case class AuthError(code: String, message: String) derives CirceCodec.AsObject, Schema

  /** A header holding one compact circe JSON document, the way `jsonQuery` does for a query. */
  def jsonHeader[T](json: Codec[String, T, CodecFormat.Json]): Codec[List[String], T, CodecFormat.TextPlain] =
    Codec.listHead(using json).format(CodecFormat.TextPlain())

  /** An optional header holding one compact circe JSON document. */
  def optionalJsonHeader[T](
      json: Codec[String, T, CodecFormat.Json]
  ): Codec[List[String], Option[T], CodecFormat.TextPlain] =
    Codec.listHeadOption(using json).format(CodecFormat.TextPlain())

  val PageSize: Int = 2

  val securedEndpoint: Endpoint[Caller, Unit, (StatusCode, AuthError), Unit, Any] = endpoint
    .securityIn(header[Caller]("X-Caller")(using jsonHeader(circeCodec[Caller])))
    .errorOut(statusCode.and(jsonBody[AuthError]))

  val listTasks: Endpoint[Caller, Int, (StatusCode, AuthError), (List[Task], PageInfo, Option[PageCursor]), Any] =
    securedEndpoint.get
      .in("tasks")
      .in(query[Int]("page").default(1))
      .out(jsonBody[List[Task]])
      .out(header[PageInfo]("X-Page-Info")(using jsonHeader(circeCodec[PageInfo])))
      .out(header[Option[PageCursor]]("X-Next-Page")(using optionalJsonHeader(circeCodec[PageCursor])))

  val createTask: Endpoint[Caller, NewTask, (StatusCode, AuthError), Task, Any] = securedEndpoint.post
    .in("tasks")
    .in(jsonBody[NewTask])
    .out(statusCode(StatusCode.Created))
    .out(jsonBody[Task])

  /** In-memory task storage shared by all tenants, filtered by the caller's tenant. */
  final class TaskBoard {
    private var tasks: Vector[Task] = Vector.empty
    private val nextId: AtomicLong = new AtomicLong()
    private val checks: AtomicLong = new AtomicLong()

    def size: Int = synchronized(tasks.size)

    def securityChecks: Long = checks.get()

    private def requireRole(role: String)(caller: Caller): Either[(StatusCode, AuthError), Caller] = {
      checks.incrementAndGet()
      if (caller.roles.contains(role)) Right(caller)
      else
        Left((StatusCode.Forbidden, AuthError("missing-role", s"${caller.user} needs $role in ${caller.tenant}")))
    }

    val serverEndpoints = List(
      listTasks
        .handleSecurity(requireRole("tasks:read"))
        .handle { caller => page =>
          if (page < 1) Left((StatusCode.BadRequest, AuthError("bad-page", s"Page $page does not exist")))
          else {
            val visible: Vector[Task] = synchronized(tasks).filter(_.tenant == caller.tenant)
            val slice: List[Task] = visible.slice((page - 1) * PageSize, page * PageSize).toList
            val next: Option[PageCursor] =
              Option.when(page * PageSize < visible.size)(PageCursor(page + 1, s"/tasks?page=${page + 1}"))
            Right((slice, PageInfo(page, PageSize, visible.size), next))
          }
        },
      createTask
        .handleSecurity(requireRole("tasks:write"))
        .handle { caller => newTask =>
          synchronized {
            val task: Task =
              Task(nextId.incrementAndGet(), caller.tenant, newTask.title, newTask.priority, caller.user)
            tasks = tasks :+ task
            Right(task)
          }
        }
    )
  }
}
