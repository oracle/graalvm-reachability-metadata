/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.doobie_free_3

import cats.effect.IO
import cats.effect.syntax.all._
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import doobie.free.{clob, connection, preparedstatement, resultset}
import doobie.hi.{connection => HC, preparedstatement => HPS}
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.sql.SQLException
import scala.concurrent.duration._

/** Exercises the CLOB subsystem to publish multilingual release notes and retrieve a marked excerpt. */
@Timeout(60)
class ReleaseNotesClobTest {
  private case class PublishedNotes(characters: Long, text: String, excerpt: String)

  @Test
  def publishedNotesPreserveUnicodeAndExposeTheUpgradeInstructions(): Unit = {
    val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      "jdbc:h2:mem:release_notes_clob;DB_CLOSE_DELAY=-1",
      "sa",
      ""
    )
    val release: String = "autumn-release"
    val upgrade: String = "Upgrade: back up the caf\u00e9 catalog before migrating."
    val notes: String =
      "Release notes\n" +
        List.fill(400)("Improved search for caf\u00e9, Stra\u00dfe, and \u6771\u4eac.\n").mkString +
        upgrade + "\nEnd of release notes.\n"

    val initialize: connection.ConnectionIO[Unit] = for {
      _ <- sql"DROP TABLE IF EXISTS release_notes".update.run
      _ <- sql"""CREATE TABLE release_notes (
                  release_name VARCHAR(60) PRIMARY KEY,
                  body CLOB NOT NULL
                )""".update.run
    } yield ()

    val publish: connection.ConnectionIO[Int] = connection.createClob.bracket { document =>
      for {
        _ <- connection.embed(document, clob.setString(1L, notes))
        inserted <- HC.prepareStatement(
          "INSERT INTO release_notes (release_name, body) VALUES (?, ?)"
        )(for {
          _ <- preparedstatement.setString(1, release)
          _ <- preparedstatement.setClob(2, document)
          count <- preparedstatement.executeUpdate
        } yield count)
      } yield inserted
    }(document => connection.embed(document, clob.free))

    val readNotes: resultset.ResultSetIO[PublishedNotes] = resultset.next.flatMap { found =>
      if (found) {
        resultset.getClob("body").bracket { document =>
          resultset.embed(document, for {
            size <- clob.length
            text <- clob.getSubString(1L, size.toInt)
            start = text.indexOf("Upgrade:")
            excerpt <- if (start >= 0) {
              clob.getSubString(start.toLong + 1L, upgrade.length)
            } else {
              clob.raiseError[String](new SQLException("Upgrade instructions missing"))
            }
          } yield PublishedNotes(size, text, excerpt))
        }(document => resultset.embed(document, clob.free))
      } else {
        resultset.raiseError[PublishedNotes](new SQLException("Published release notes missing"))
      }
    }

    val retrieve: connection.ConnectionIO[PublishedNotes] = HC.prepareStatement(
      "SELECT body FROM release_notes WHERE release_name = ?"
    )(for {
      _ <- preparedstatement.setString(1, release)
      document <- HPS.executeQuery(readNotes)
    } yield document)

    val (inserted, published): (Int, PublishedNotes) = (for {
      _ <- initialize.transact(transactor)
      count <- publish.transact(transactor)
      document <- retrieve.transact(transactor)
      _ <- sql"DROP TABLE release_notes".update.run.transact(transactor)
    } yield (count, document)).timeout(45.seconds).unsafeRunSync()

    assertEquals(1, inserted)
    assertEquals(notes.length.toLong, published.characters)
    assertEquals(notes, published.text)
    assertEquals(upgrade, published.excerpt)
  }
}
