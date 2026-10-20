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
import cats.syntax.all.*
import com.comcast.ip4s.*
import fs2.Stream
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.headers.Location
import org.http4s.implicits.*
import org.http4s.multipart.Multipart
import org.http4s.multipart.Multiparts
import org.http4s.multipart.Part
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Multipart form-data file uploads decoded by routes written with the http4s DSL.
 *
 * A photo album service accepts `multipart/form-data` posts that carry a text caption, one photo
 * file and any number of attachment files. The route decodes the body with
 * `request.decode[Multipart[IO]]`, reads the parts by name, keeps each file's name, media type and
 * bytes, and answers `201 Created` with a `Location` the client then downloads from. A post with no
 * photo part is refused with `400`, and a plain-text post the multipart decoder cannot parse is
 * refused with `422`.
 * Requests are built with `Multiparts` and `Part` and sent by an Ember client to an Ember server.
 */
class PhotoAlbumMultipartUploadTest {
  import PhotoAlbumMultipartUploadTest.*

  @Test
  def albumAcceptsMultipartUploadsAndServesStoredFiles(): Unit = {
    val outcome: Observed = serveAndCall { (client: Client[IO], base: Uri) =>
      val photos: Uri = base / "albums" / "holiday" / "photos"
      for {
        multiparts <- Multiparts.forSync[IO]
        upload <- multiparts.multipart(
          Vector(
            Part.formData[IO]("caption", "Sunset over the bay"),
            Part.fileData[IO](
              "photo",
              "sunset.png",
              Stream.emits(PngBytes.toSeq).covary[IO],
              `Content-Type`(MediaType.image.png)
            ),
            Part.fileData[IO](
              "attachment",
              "notes.txt",
              Stream.emits("shot at 19:42".getBytes("UTF-8").toSeq).covary[IO],
              `Content-Type`(MediaType.text.plain)
            ),
            Part.fileData[IO](
              "attachment",
              "gps.csv",
              Stream.emits("lat,lon\n43.5,16.4".getBytes("UTF-8").toSeq).covary[IO],
              `Content-Type`(MediaType.text.csv)
            )
          )
        )
        created <- client
          .run(Request[IO](Method.POST, photos).withEntity(upload).withHeaders(upload.headers))
          .use(response =>
            response.as[String].map(body => (response.status, response.headers.get[Location], body))
          )
        photo <- download(client, base / "albums" / "holiday" / "photos" / "sunset.png")
        notes <- download(client, base / "albums" / "holiday" / "photos" / "sunset.png" / "notes.txt")
        gps <- download(client, base / "albums" / "holiday" / "photos" / "sunset.png" / "gps.csv")
        listed <- call(client, Request[IO](Method.GET, photos))
        captionOnly <- multiparts.multipart(Vector(Part.formData[IO]("caption", "No file here")))
        missingPhoto <- call(
          client,
          Request[IO](Method.POST, photos).withEntity(captionOnly).withHeaders(captionOnly.headers)
        )
        notMultipart <- client
          .run(Request[IO](Method.POST, photos).withEntity("caption=plain"))
          .use(response => IO.pure(response.status))
      } yield Observed(created, photo, notes, gps, listed, missingPhoto, notMultipart)
    }

    assertEquals(Status.Created, outcome.created._1)
    assertEquals(Some(Location(uri"/albums/holiday/photos/sunset.png")), outcome.created._2)
    assertEquals("sunset.png (8 bytes, image/png) caption=Sunset over the bay attachments=2", outcome.created._3)

    assertEquals(Status.Ok, outcome.photo.status)
    assertEquals(Some(MediaType.image.png), outcome.photo.mediaType)
    assertArrayEquals(PngBytes, outcome.photo.bytes)

    assertEquals(Status.Ok, outcome.notes.status)
    assertEquals(Some(MediaType.text.plain), outcome.notes.mediaType)
    assertEquals("shot at 19:42", new String(outcome.notes.bytes, "UTF-8"))

    assertEquals(Status.Ok, outcome.gps.status)
    assertEquals(Some(MediaType.text.csv), outcome.gps.mediaType)
    assertEquals("lat,lon\n43.5,16.4", new String(outcome.gps.bytes, "UTF-8"))

    assertEquals((Status.Ok, "sunset.png: Sunset over the bay [notes.txt, gps.csv]"), outcome.listed)
    assertEquals((Status.BadRequest, "a photo part is required"), outcome.missingPhoto)
    assertEquals(Status.UnprocessableContent, outcome.notMultipart)
  }
}

object PhotoAlbumMultipartUploadTest {

  /** The PNG signature, standing in for an uploaded image file. */
  val PngBytes: Array[Byte] = Array(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a).map(_.toByte)

  /** What the client saw when it downloaded one stored file. */
  final case class Download(status: Status, mediaType: Option[MediaType], bytes: Array[Byte])

  /** What the client saw at each step of the upload session. */
  final case class Observed(
      created: (Status, Option[Location], String),
      photo: Download,
      notes: Download,
      gps: Download,
      listed: (Status, String),
      missingPhoto: (Status, String),
      notMultipart: Status
  )

  /** One uploaded file as the album keeps it. */
  final case class StoredFile(name: String, mediaType: MediaType, bytes: Array[Byte])

  /** A photo with its caption and the files attached to it, in upload order. */
  final case class Photo(caption: String, file: StoredFile, attachments: Vector[StoredFile])

  /** Reads one file part into memory, defaulting to an octet stream when no type was sent. */
  def storeFile(part: Part[IO]): IO[StoredFile] =
    part.body.compile.to(Array).map { bytes =>
      StoredFile(
        part.filename.getOrElse("unnamed"),
        part.headers.get[`Content-Type`].map(_.mediaType).getOrElse(MediaType.application.`octet-stream`),
        bytes
      )
    }

  /** Answers a stored file with the media type it was uploaded with. */
  def serveFile(file: StoredFile): IO[Response[IO]] =
    Ok(file.bytes, `Content-Type`(file.mediaType))

  /** The album API: uploads are keyed by album name and then by photo file name. */
  def albumRoutes(albums: Ref[IO, Map[String, Map[String, Photo]]]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case request @ POST -> Root / "albums" / album / "photos" =>
      request.decode[Multipart[IO]] { multipart =>
        val named: String => Vector[Part[IO]] = name => multipart.parts.filter(_.name.contains(name))
        named("photo").headOption match {
          case None => BadRequest("a photo part is required")
          case Some(photoPart) =>
            for {
              caption <- named("caption").headOption.traverse(_.bodyText.compile.string)
              file <- storeFile(photoPart)
              attachments <- named("attachment").traverse(storeFile)
              photo = Photo(caption.getOrElse(""), file, attachments)
              _ <- albums.update(state =>
                state.updated(album, state.getOrElse(album, Map.empty).updated(file.name, photo))
              )
              response <- Created(
                s"${file.name} (${file.bytes.length} bytes, ${file.mediaType.mainType}/${file.mediaType.subType}) " +
                  s"caption=${photo.caption} attachments=${attachments.size}",
                Location(uri"/albums" / album / "photos" / file.name)
              )
            } yield response
        }
      }

    case GET -> Root / "albums" / album / "photos" =>
      albums.get.flatMap { state =>
        val photos: List[Photo] = state.getOrElse(album, Map.empty).values.toList.sortBy(_.file.name)
        Ok(
          photos
            .map(photo => s"${photo.file.name}: ${photo.caption} ${photo.attachments.map(_.name).mkString("[", ", ", "]")}")
            .mkString("\n")
        )
      }

    case GET -> Root / "albums" / album / "photos" / photoName =>
      albums.get.map(_.get(album).flatMap(_.get(photoName))).flatMap {
        case Some(photo) => serveFile(photo.file)
        case None => NotFound(s"no photo $photoName")
      }

    case GET -> Root / "albums" / album / "photos" / photoName / attachmentName =>
      albums.get
        .map(_.get(album).flatMap(_.get(photoName)).flatMap(_.attachments.find(_.name == attachmentName)))
        .flatMap {
          case Some(file) => serveFile(file)
          case None => NotFound(s"no attachment $attachmentName")
        }
  }

  /** Starts the service on an ephemeral loopback port and runs `scenario` against it. */
  def serveAndCall[A](scenario: (Client[IO], Uri) => IO[A]): A = {
    val running: Resource[IO, (Client[IO], Uri)] = for {
      albums <- Resource.eval(Ref.of[IO, Map[String, Map[String, Photo]]](Map.empty))
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(albumRoutes(albums).orNotFound)
        .build
      client <- EmberClientBuilder.default[IO].build
    } yield (client, Uri.unsafeFromString(s"http://127.0.0.1:${server.address.getPort}"))
    running.use((client, base) => scenario(client, base)).unsafeRunSync()
  }

  /** Downloads `uri` and keeps the status, media type and raw bytes of the response. */
  def download(client: Client[IO], uri: Uri): IO[Download] =
    client.run(Request[IO](Method.GET, uri)).use { response =>
      response.body.compile.to(Array).map { bytes =>
        Download(response.status, response.headers.get[`Content-Type`].map(_.mediaType), bytes)
      }
    }

  /** Sends `request` and reads the whole response body as text. */
  def call(client: Client[IO], request: Request[IO]): IO[(Status, String)] =
    client.run(request).use(response => response.as[String].map(body => (response.status, body)))
}
