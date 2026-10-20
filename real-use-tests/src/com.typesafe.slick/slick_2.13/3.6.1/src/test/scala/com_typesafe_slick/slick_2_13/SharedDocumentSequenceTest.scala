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
import slick.lifted.ProvenShape

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/** Exercises Slick's database sequence subsystem to number invoices and credit notes together.
  * Accounting documents reserve their references before insertion from a shared numbering series.
  */
class SharedDocumentSequenceTest {
  class Invoices(tag: Tag) extends Table[(Long, String, Int)](tag, "NUMBERED_INVOICES") {
    def number: Rep[Long] = column[Long]("NUMBER", O.PrimaryKey)
    def customer: Rep[String] = column[String]("CUSTOMER")
    def cents: Rep[Int] = column[Int]("CENTS")
    def * : ProvenShape[(Long, String, Int)] = (number, customer, cents)
  }

  class CreditNotes(tag: Tag, invoices: TableQuery[Invoices])
      extends Table[(Long, Long, Int)](tag, "NUMBERED_CREDIT_NOTES") {
    def number: Rep[Long] = column[Long]("NUMBER", O.PrimaryKey)
    def invoiceNumber: Rep[Long] = column[Long]("INVOICE_NUMBER")
    def cents: Rep[Int] = column[Int]("CENTS")
    def invoice = foreignKey("NUMBERED_CREDIT_INVOICE_FK", invoiceNumber, invoices)(_.number)
    def * : ProvenShape[(Long, Long, Int)] = (number, invoiceNumber, cents)
  }

  @Test
  @Timeout(60)
  def allocatesOneDocumentSeriesAcrossInvoicesAndCredits(): Unit = {
    val invoices: TableQuery[Invoices] = TableQuery(tag => new Invoices(tag))
    val credits: TableQuery[CreditNotes] = TableQuery(tag => new CreditNotes(tag, invoices))
    val documentNumbers: Sequence[Long] = Sequence[Long]("ACCOUNTING_DOCUMENT_NUMBER")
    val schema = documentNumbers.schema ++ invoices.schema ++ credits.schema
    val nextNumber: DBIO[Long] = Query(documentNumbers.next).result.head
    val database: Database = Database.forURL(
      "jdbc:h2:mem:shared_document_sequence;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )

    def issueInvoice(customer: String, cents: Int): DBIO[(Long, String, Int)] = (for {
      number <- nextNumber
      row = (number, customer, cents)
      _ <- invoices += row
    } yield row).transactionally

    def issueCredit(invoiceNumber: Long, cents: Int): DBIO[(Long, Long, Int)] = (for {
      number <- nextNumber
      row = (number, invoiceNumber, cents)
      _ <- credits += row
    } yield row).transactionally

    val creditRegister = (for {
      credit <- credits
      invoice <- invoices if invoice.number === credit.invoiceNumber
    } yield (credit.number, invoice.number, invoice.customer, credit.cents)).sortBy(_._1)

    val scenario = for {
      _ <- schema.create
      firstInvoice <- issueInvoice("Ada", 12000)
      firstCredit <- issueCredit(firstInvoice._1, 2000)
      secondInvoice <- issueInvoice("Grace", 7500)
      secondCredit <- issueCredit(firstInvoice._1, 1000)
      savedInvoices <- invoices.sortBy(_.number).result
      savedCredits <- credits.sortBy(_.number).result
      register <- creditRegister.result
    } yield (firstInvoice, firstCredit, secondInvoice, secondCredit,
      savedInvoices, savedCredits, register)

    try {
      val (firstInvoice, firstCredit, secondInvoice, secondCredit,
        savedInvoices, savedCredits, register) = Await.result(
        database.run(scenario.andFinally(schema.drop)),
        30.seconds
      )
      assertEquals((1L, "Ada", 12000), firstInvoice)
      assertEquals((2L, 1L, 2000), firstCredit)
      assertEquals((3L, "Grace", 7500), secondInvoice)
      assertEquals((4L, 1L, 1000), secondCredit)
      assertEquals(Seq(firstInvoice, secondInvoice), savedInvoices)
      assertEquals(Seq(firstCredit, secondCredit), savedCredits)
      assertEquals(
        Seq((2L, 1L, "Ada", 2000), (4L, 1L, "Ada", 1000)),
        register
      )
    } finally {
      database.close()
    }
  }
}
