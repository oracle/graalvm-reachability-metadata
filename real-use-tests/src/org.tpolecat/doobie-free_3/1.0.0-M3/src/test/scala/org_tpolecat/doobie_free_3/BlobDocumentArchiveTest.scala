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
import doobie.free.{blob, connection, preparedstatement, resultset}
import doobie.hi.{connection => HC, preparedstatement => HPS}
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.junit.jupiter.api.Assertions.{assertArrayEquals, assertEquals}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.sql.SQLException
import scala.concurrent.duration._

/** Exercises the BLOB subsystem to archive and retrieve a binary document. */
@Timeout(60)
class BlobDocumentArchiveTest {
  @Test
  def archivedDocumentRetainsEveryByteAfterCommit(): Unit = {
    val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      "jdbc:h2:mem:blob_document_archive;DB_CLOSE_DELAY=-1",
      "sa",
      ""
    )
    val documentId: String = "invoice-2048"
    val original: Array[Byte] = Array.tabulate[Byte](8192)(index => (index % 256).toByte)

    val initialize: connection.ConnectionIO[Unit] = for {
      _ <- sql"DROP TABLE IF EXISTS archived_documents".update.run
      _ <- sql"""CREATE TABLE archived_documents (
                  document_id VARCHAR(40) PRIMARY KEY,
                  content BLOB NOT NULL
                )""".update.run
    } yield ()

    val archive: connection.ConnectionIO[Int] = connection.createBlob.bracket { content =>
      for {
        _ <- connection.embed(content, blob.setBytes(1L, original))
        inserted <- HC.prepareStatement(
          "INSERT INTO archived_documents (document_id, content) VALUES (?, ?)"
        )(for {
          _ <- preparedstatement.setString(1, documentId)
          _ <- preparedstatement.setBlob(2, content)
          count <- preparedstatement.executeUpdate
        } yield count)
      } yield inserted
    }(content => connection.embed(content, blob.free))

    val readContent: resultset.ResultSetIO[(Long, Array[Byte])] = for {
      found <- resultset.next
      document <- if (found) {
        resultset.getBlob("content").bracket { content =>
          resultset.embed(content, for {
            size <- blob.length
            bytes <- blob.getBytes(1L, size.toInt)
          } yield (size, bytes))
        }(content => resultset.embed(content, blob.free))
      } else {
        resultset.raiseError[(Long, Array[Byte])](new SQLException("Archived document not found"))
      }
    } yield document

    val retrieve: connection.ConnectionIO[(Long, Array[Byte])] = HC.prepareStatement(
      "SELECT content FROM archived_documents WHERE document_id = ?"
    )(for {
      _ <- preparedstatement.setString(1, documentId)
      document <- HPS.executeQuery(readContent)
    } yield document)

    val (inserted, size, restored): (Int, Long, Array[Byte]) = (for {
      _ <- initialize.transact(transactor)
      count <- archive.transact(transactor)
      document <- retrieve.transact(transactor)
      _ <- sql"DROP TABLE archived_documents".update.run.transact(transactor)
    } yield (count, document._1, document._2)).timeout(45.seconds).unsafeRunSync()

    assertEquals(1, inserted)
    assertEquals(original.length.toLong, size)
    assertArrayEquals(original, restored)
  }
}
