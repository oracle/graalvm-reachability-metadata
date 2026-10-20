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
import doobie.free.{connection, nclob, preparedstatement, resultset}
import doobie.hi.{connection => HC, preparedstatement => HPS}
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.io.StringWriter
import java.sql.SQLException
import scala.concurrent.duration._

/** Exercises NCLOB character streams to persist and render localized notification templates. */
@Timeout(60)
class LocalizedNotificationNClobTest {
  private case class Template(locale: String, body: String)

  private def publish(template: Template): connection.ConnectionIO[Unit] =
    connection.createNClob.bracket { content =>
      for {
        _ <- connection.embed(content,
          nclob.setCharacterStream(1L).bracket { writer =>
            nclob.delay(writer.write(template.body))
          }(writer => nclob.delay(writer.close()))
        )
        _ <- HC.prepareStatement("INSERT INTO notification_templates (locale, body) VALUES (?, ?)")(for {
          _ <- preparedstatement.setString(1, template.locale)
          _ <- preparedstatement.setNClob(2, content)
          _ <- preparedstatement.executeUpdate
        } yield ())
      } yield ()
    }(content => connection.embed(content, nclob.free))

  private def render(locale: String, customer: String, order: String): connection.ConnectionIO[String] = {
    val readTemplate: resultset.ResultSetIO[String] = resultset.next.flatMap { found =>
      if (found) {
        resultset.getNClob("body").bracket { content =>
          resultset.embed(content,
            nclob.getCharacterStream.bracket { reader =>
              nclob.delay {
                val text: StringWriter = new StringWriter()
                reader.transferTo(text)
                text.toString
              }
            }(reader => nclob.delay(reader.close()))
          )
        }(content => resultset.embed(content, nclob.free))
      } else {
        resultset.raiseError[String](new SQLException(s"Missing notification locale: $locale"))
      }
    }

    HC.prepareStatement("SELECT body FROM notification_templates WHERE locale = ?")(for {
      _ <- preparedstatement.setString(1, locale)
      body <- HPS.executeQuery(readTemplate)
    } yield body.replace("{{customer}}", customer).replace("{{order}}", order))
  }

  @Test
  def notificationsRetainNationalCharactersAndSupplementarySymbolsAfterCommit(): Unit = {
    val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      "jdbc:h2:mem:localized_notification_nclob;DB_CLOSE_DELAY=-1",
      "sa",
      ""
    )
    val templates: List[Template] = List(
      Template("ja", "{{customer}}\u69d8\n\u3054\u6ce8\u6587 {{order}} \u3092\u767a\u9001\u3057\u307e\u3057\u305f\u3002 \ud83d\udce6"),
      Template("ar", "\u0645\u0631\u062d\u0628\u0627 {{customer}}\n{{order}} \u062a\u0645 \u0627\u0644\u0634\u062d\u0646 \ud83d\udce6")
    )

    val initialize: connection.ConnectionIO[Unit] = for {
      _ <- sql"DROP TABLE IF EXISTS notification_templates".update.run
      _ <- sql"""CREATE TABLE notification_templates (
                  locale VARCHAR(10) PRIMARY KEY,
                  body NCLOB NOT NULL
                )""".update.run
    } yield ()

    val (japanese, arabic): (String, String) = (for {
      _ <- initialize.transact(transactor)
      _ <- templates.traverse_(publish).transact(transactor)
      first <- render("ja", "\u7530\u4e2d", "JP-1042").transact(transactor)
      second <- render("ar", "\u0646\u0648\u0631", "AR-2048").transact(transactor)
      _ <- sql"DROP TABLE notification_templates".update.run.transact(transactor)
    } yield (first, second)).timeout(45.seconds).unsafeRunSync()

    assertEquals("\u7530\u4e2d\u69d8\n\u3054\u6ce8\u6587 JP-1042 \u3092\u767a\u9001\u3057\u307e\u3057\u305f\u3002 \ud83d\udce6", japanese)
    assertEquals("\u0645\u0631\u062d\u0628\u0627 \u0646\u0648\u0631\nAR-2048 \u062a\u0645 \u0627\u0644\u0634\u062d\u0646 \ud83d\udce6", arabic)
  }
}
