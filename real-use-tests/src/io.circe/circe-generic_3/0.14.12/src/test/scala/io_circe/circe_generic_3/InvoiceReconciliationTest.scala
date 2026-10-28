/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.CursorOp
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.parser.decode
import io.circe.parser.parse
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises derived decoder post-validation for cross-field invoice reconciliation. */
@Timeout(60)
class InvoiceReconciliationTest {
  final case class InvoiceLine(description: String, quantity: Int, unitPrice: BigDecimal)

  object InvoiceLine {
    given Decoder[InvoiceLine] = deriveDecoder[InvoiceLine]
    given Encoder.AsObject[InvoiceLine] = deriveEncoder[InvoiceLine]
  }

  final case class Invoice(number: String, lines: List[InvoiceLine], declaredTotal: BigDecimal)

  object Invoice {
    given Decoder[Invoice] = deriveDecoder[Invoice]
      .ensure(_.lines.nonEmpty, "Invoice must contain at least one line")
      .ensure(
        invoice => invoice.lines.map(line => line.unitPrice * line.quantity).sum == invoice.declaredTotal,
        "Declared total does not match invoice lines"
      )
    given Encoder.AsObject[Invoice] = deriveEncoder[Invoice]
  }

  @Test
  def rejectsUnreconciledInvoicesAndArchivesTheCorrectedSubmission(): Unit = {
    val submission: String = """
      {
        "number": "INV-204",
        "lines": [
          {"description": "Notebooks", "quantity": 2, "unitPrice": 4.25},
          {"description": "Pencils", "quantity": 3, "unitPrice": 0.50}
        ],
        "declaredTotal": 11.00
      }
      """
    val mismatch: DecodingFailure = rejection(submission)
    assertThat(mismatch.message).isEqualTo("Declared total does not match invoice lines")

    val empty: DecodingFailure = rejection("""
      {"number": "INV-205", "lines": [], "declaredTotal": 0}
      """)
    assertThat(empty.message).isEqualTo("Invoice must contain at least one line")

    // Structural errors still identify the bad field before reconciliation can run.
    val malformed: DecodingFailure = rejection(submission.replace("\"quantity\": 2", "\"quantity\": false"))
    assertThat(CursorOp.opsToPath(malformed.history)).isEqualTo(".lines[0].quantity")

    val corrected: String = submission.replace("11.00", "10.00")
    val invoice: Invoice = decode[Invoice](corrected).fold(
      error => throw new AssertionError(s"Reconciled invoice was rejected: $error"),
      identity
    )
    assertThat(invoice).isEqualTo(Invoice("INV-204", List(
      InvoiceLine("Notebooks", 2, BigDecimal("4.25")),
      InvoiceLine("Pencils", 3, BigDecimal("0.50"))
    ), BigDecimal("10.00")))

    val archived: Json = invoice.asJson
    val expectedArchive: Json = parse(corrected).fold(
      error => throw new AssertionError(s"Invalid invoice fixture: $error"),
      identity
    )
    assertThat(archived).isEqualTo(expectedArchive)
    assertThat(decode[Invoice](archived.noSpaces)).isEqualTo(Right(invoice))
    assertThat(invoice.declaredTotal).isEqualTo(BigDecimal("10.00"))
  }

  private def rejection(input: String): DecodingFailure = decode[Invoice](input).fold(
    {
      case error: DecodingFailure => error
      case error => throw new AssertionError(s"Expected an invoice decoding failure: $error")
    },
    _ => throw new AssertionError("Invalid invoice was accepted")
  )
}
