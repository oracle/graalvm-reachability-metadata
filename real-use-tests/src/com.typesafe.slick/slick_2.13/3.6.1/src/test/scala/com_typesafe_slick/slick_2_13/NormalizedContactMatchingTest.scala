/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import slick.jdbc.H2Profile.api._
import slick.lifted.{ProvenShape, SimpleFunction}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/** Exercises Slick's SQL scalar-function extension subsystem for normalized contact matching.
  * Database-side phone normalization finds duplicate imports without rewriting display formatting.
  */
class NormalizedContactMatchingTest {
  case class Contact(id: Int, tenant: Int, name: String, phone: String, active: Boolean)

  class Contacts(tag: Tag) extends Table[Contact](tag, "NORMALIZED_CONTACTS") {
    def id: Rep[Int] = column[Int]("ID", O.PrimaryKey)
    def tenant: Rep[Int] = column[Int]("TENANT")
    def name: Rep[String] = column[String]("NAME")
    def phone: Rep[String] = column[String]("PHONE")
    def active: Rep[Boolean] = column[Boolean]("ACTIVE")
    def * : ProvenShape[Contact] = (id, tenant, name, phone, active).mapTo[Contact]
  }

  private val regexpReplace: (Rep[String], Rep[String], Rep[String]) => Rep[String] =
    SimpleFunction.ternary[String, String, String, String]("REGEXP_REPLACE")

  private def phoneDigits(phone: Rep[String]): Rep[String] =
    regexpReplace(phone, LiteralColumn("[^0-9]"), LiteralColumn(""))

  @Test
  @Timeout(60)
  def findsFormattedDuplicatesAndRefreshesAfterCorrection(): Unit = {
    val contacts: TableQuery[Contacts] = TableQuery(tag => new Contacts(tag))
    val database: Database = Database.forURL(
      "jdbc:h2:mem:normalized_contact_matching;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )
    val initial: Seq[Contact] = Seq(
      Contact(1, 10, "Ada", "+1 (202) 555-0100", true),
      Contact(2, 10, "Ada imported", "1.202.555.0100", true),
      Contact(3, 10, "Grace", "1-202-555-0199", true),
      Contact(4, 20, "Other tenant", "12025550100", true),
      Contact(5, 10, "Archived", "12025550100", false),
      Contact(6, 10, "Missing phone", "--", true),
      Contact(7, 10, "Blank phone", " ", true)
    )
    val eligible = contacts.filter(contact =>
      contact.tenant === 10 && contact.active && phoneDigits(contact.phone) =!= ""
    )
    val duplicatePairs = (for {
      first <- eligible
      second <- eligible if first.id < second.id &&
        phoneDigits(first.phone) === phoneDigits(second.phone)
    } yield (first.name, second.name, phoneDigits(first.phone))).sortBy(row => (row._1, row._2))

    def lookup(phone: String): DBIO[Seq[(Int, String, String)]] =
      eligible.filter(contact => phoneDigits(contact.phone) === phoneDigits(phone.bind))
        .sortBy(_.id).map(contact => (contact.id, contact.name, contact.phone)).result

    val scenario = for {
      _ <- contacts.schema.create
      _ <- contacts ++= initial
      duplicates <- duplicatePairs.result
      matched <- lookup("+1 202 555 0100")
      saved <- contacts.sortBy(_.id).result
      corrected <- contacts.filter(_.id === 2).map(_.phone).update("1 (202) 555-0123")
      afterCorrection <- duplicatePairs.result
      remainingMatch <- lookup("+1 202 555 0100")
      correctedMatch <- lookup("12025550123")
    } yield (duplicates, matched, saved, corrected, afterCorrection, remainingMatch, correctedMatch)

    try {
      val (duplicates, matched, saved, corrected, afterCorrection, remainingMatch, correctedMatch) =
        Await.result(database.run(scenario.andFinally(contacts.schema.drop)), 30.seconds)
      assertEquals(Seq(("Ada", "Ada imported", "12025550100")), duplicates)
      assertEquals(
        Seq((1, "Ada", "+1 (202) 555-0100"), (2, "Ada imported", "1.202.555.0100")),
        matched
      )
      assertEquals(initial, saved)
      assertEquals(1, corrected)
      assertEquals(Seq.empty[(String, String, String)], afterCorrection)
      assertEquals(Seq((1, "Ada", "+1 (202) 555-0100")), remainingMatch)
      assertEquals(Seq((2, "Ada imported", "1 (202) 555-0123")), correctedMatch)
    } finally {
      database.close()
    }
  }
}
