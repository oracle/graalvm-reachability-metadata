/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_scalaland.chimney_3

import io.scalaland.chimney.PartialTransformer
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.partial
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Target constructor overrides (`withConstructor`, `withConstructorPartial`,
 * `withConstructorEither`, `withConstructorTo`) combined with custom field name comparison.
 *
 * A billing service imports invoice drafts from its intake queue. The domain invoice normalizes
 * its number and customer and groups the amount into a `Money` value, so instead of the primary
 * constructor Chimney calls application-supplied factory functions whose parameters are matched
 * by name against the draft's fields. A strict import validates inside the factory and reports
 * the failure as a partial result, a nested `Money` is built through a constructor attached to
 * its path, and rows from a legacy mainframe export with upper-case column names are matched
 * case-insensitively.
 */
class InvoiceImportConstructorOverridesTest {

  final case class InvoiceDraft(number: String, customer: String, amountCents: Long, currency: String, lineCount: Int)

  final case class Money(cents: Long, currency: String)
  final case class Invoice(number: String, customer: String, total: Money, lines: Int)

  final case class AmountDraft(amountCents: Long, currency: String)
  final case class PaymentDraft(reference: String, amount: AmountDraft)
  final case class Payment(reference: String, amount: Money)

  final case class LegacyInvoiceRow(NUMBER: String, CUSTOMER: String, LINECOUNT: Int, BRANCH: String)
  final case class InvoiceSummary(number: String, customer: String, lineCount: Int)

  private val supportedCurrencies: Set[String] = Set("EUR", "USD", "CHF")

  /** Every derived invoice mapping goes through the normalizing factory instead of `Invoice.apply`. */
  given Transformer[InvoiceDraft, Invoice] = Transformer
    .define[InvoiceDraft, Invoice]
    .withConstructor { (number: String, customer: String, amountCents: Long, currency: String, lineCount: Int) =>
      Invoice(number.trim.toUpperCase, customer.trim, Money(amountCents, currency.toUpperCase), lineCount)
    }
    .buildTransformer

  /** The strict import rejects negative amounts and unknown currencies, reporting both at once. */
  private val strictImport: PartialTransformer[InvoiceDraft, Invoice] = PartialTransformer
    .define[InvoiceDraft, Invoice]
    .withConstructorPartial { (number: String, customer: String, amountCents: Long, currency: String, lineCount: Int) =>
      val amount: partial.Result[Long] =
        if (amountCents >= 0) partial.Result.fromValue(amountCents)
        else partial.Result.fromErrorString(s"negative amount $amountCents")
      val code: partial.Result[String] =
        if (supportedCurrencies.contains(currency)) partial.Result.fromValue(currency)
        else partial.Result.fromErrorString(s"unsupported currency $currency")
      partial.Result.map2(
        amount,
        code,
        (cents: Long, validCode: String) => Invoice(number, customer, Money(cents, validCode), lineCount),
        false
      )
    }
    .buildTransformer

  @Test
  def factoryReplacesPrimaryConstructorForOneOffConversion(): Unit = {
    val draft: InvoiceDraft = InvoiceDraft(" inv-2026-001 ", "  Acme GmbH ", 129900L, "eur", 3)

    val invoice: Invoice = draft
      .into[Invoice]
      .withConstructor { (number: String, customer: String, amountCents: Long, currency: String, lineCount: Int) =>
        Invoice(s"DRAFT-$number".trim, customer, Money(amountCents * 2, currency), lineCount + 1)
      }
      .transform

    assertEquals(Invoice("DRAFT- inv-2026-001", "  Acme GmbH ", Money(259800L, "eur"), 4), invoice)
  }

  @Test
  def givenTransformerWithFactoryIsReusedForWholeBatches(): Unit = {
    val batch: List[InvoiceDraft] = List(
      InvoiceDraft("inv-1", " Acme ", 1000L, "eur", 1),
      InvoiceDraft(" inv-2", "Globex  ", 2550L, "usd", 2)
    )

    val single: Invoice = batch.head.transformInto[Invoice]
    val imported: Vector[Invoice] = batch.transformInto[Vector[Invoice]]
    val byNumber: Map[String, Invoice] = Map("a" -> batch.head, "b" -> batch(1)).transformInto[Map[String, Invoice]]

    assertEquals(Invoice("INV-1", "Acme", Money(1000L, "EUR"), 1), single)
    assertEquals(
      Vector(
        Invoice("INV-1", "Acme", Money(1000L, "EUR"), 1),
        Invoice("INV-2", "Globex", Money(2550L, "USD"), 2)
      ),
      imported
    )
    assertEquals(Some("INV-2"), byNumber.get("b").map(_.number))
  }

  @Test
  def partialFactoryAcceptsValidDraftAndAccumulatesFactoryErrors(): Unit = {
    val valid: partial.Result[Invoice] = strictImport.transform(InvoiceDraft("INV-7", "Initech", 4200L, "CHF", 5))
    val invalid: partial.Result[Invoice] = strictImport.transform(InvoiceDraft("INV-8", "Initech", -5L, "XAU", 1))

    assertEquals(Right(Invoice("INV-7", "Initech", Money(4200L, "CHF"), 5)), valid.asEither)
    assertEquals(None, invalid.asOption)
    assertEquals(
      List("negative amount -5", "unsupported currency XAU"),
      invalid.asErrorPathMessageStrings.map(_._2).toList
    )
  }

  @Test
  def eitherFactoryTurnsLeftIntoPartialError(): Unit = {
    def importDraft(draft: InvoiceDraft): partial.Result[Invoice] = draft
      .intoPartial[Invoice]
      .withConstructorEither { (number: String, customer: String, amountCents: Long, currency: String, lineCount: Int) =>
        if (lineCount > 0) Right(Invoice(number, customer, Money(amountCents, currency), lineCount))
        else Left(s"invoice $number has no lines")
      }
      .transform

    assertEquals(
      Some(Invoice("INV-9", "Umbrella", Money(990L, "EUR"), 2)),
      importDraft(InvoiceDraft("INV-9", "Umbrella", 990L, "EUR", 2)).asOption
    )
    assertEquals(
      List("invoice INV-10 has no lines"),
      importDraft(InvoiceDraft("INV-10", "Umbrella", 0L, "EUR", 0)).asErrorPathMessageStrings.map(_._2).toList
    )
  }

  @Test
  def nestedConstructorAttachedToTargetPathBuildsMoney(): Unit = {
    val drafts: List[PaymentDraft] = List(
      PaymentDraft("PAY-1", AmountDraft(1500L, "eur")),
      PaymentDraft("PAY-2", AmountDraft(75L, "usd"))
    )

    val payments: List[Payment] = drafts.map { draft =>
      draft
        .into[Payment]
        .withConstructorTo(_.amount) { (amountCents: Long, currency: String) =>
          Money(amountCents, currency.toUpperCase)
        }
        .transform
    }

    assertEquals(
      List(Payment("PAY-1", Money(1500L, "EUR")), Payment("PAY-2", Money(75L, "USD"))),
      payments
    )
  }

  @Test
  def legacyUpperCaseColumnsAreMatchedCaseInsensitively(): Unit = {
    val row: LegacyInvoiceRow = LegacyInvoiceRow("INV-3", "Hooli", 4, "MAINFRAME-07")

    val summary: InvoiceSummary = row
      .into[InvoiceSummary]
      .enableCustomFieldNameComparison(TransformedNamesComparison.CaseInsensitiveEquality)
      .transform
    val labeled: InvoiceSummary = row
      .into[InvoiceSummary]
      .enableCustomFieldNameComparison(TransformedNamesComparison.CaseInsensitiveEquality)
      .withConstructor { (number: String, customer: String, lineCount: Int) =>
        InvoiceSummary(s"$number/$lineCount", customer.toUpperCase, lineCount)
      }
      .transform

    assertEquals(InvoiceSummary("INV-3", "Hooli", 4), summary)
    assertEquals(InvoiceSummary("INV-3/4", "HOOLI", 4), labeled)
  }
}
