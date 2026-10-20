/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_core

import cats.data.EitherT
import cats.syntax.all.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Cats EitherT to compose optional applicant lookups with loan eligibility decisions.
  * Missing records remain distinct from business rejections and approved offers.
  */
@Timeout(60)
class LoanOfferEitherTTest {
  private case class Applicant(id: String, monthlyIncomeCents: Long, creditScore: Int)

  private case class Offer(applicantId: String, principalCents: Long, annualRateBasisPoints: Int)

  private type Decision[A] = EitherT[Option, String, A]

  private val applicants: Map[String, Applicant] = Map(
    "prime" -> Applicant("prime", 400000L, 780),
    "standard" -> Applicant("standard", 300000L, 680),
    "declined" -> Applicant("declined", 400000L, 550)
  )

  private val requestedAmounts: Map[String, Long] = Map(
    "small" -> 600000L,
    "large" -> 3000000L,
    "invalid" -> 0L
  )

  private def lookupApplicant(id: String): Decision[Applicant] = {
    EitherT.liftF[Option, String, Applicant](applicants.get(id))
  }

  private def checkCredit(applicant: Applicant): Decision[Applicant] = {
    EitherT.fromEither[Option](Either.cond(
      applicant.creditScore >= 650,
      applicant,
      "credit score below minimum"
    ))
  }

  private def requestedAmount(requestId: String): Decision[Long] = {
    EitherT.liftF[Option, String, Long](requestedAmounts.get(requestId))
  }

  private def checkAffordability(applicant: Applicant, principal: Long): Decision[Long] = {
    val checked: Either[String, Long] = if (principal <= 0L) {
      Left("principal must be positive")
    } else if (principal > applicant.monthlyIncomeCents * 6L) {
      Left("principal exceeds income limit")
    } else {
      Right(principal)
    }
    EitherT.fromEither[Option](checked)
  }

  private def offer(applicantId: String, requestId: String): Decision[Offer] = {
    for {
      applicant <- lookupApplicant(applicantId)
      eligible <- checkCredit(applicant)
      requested <- requestedAmount(requestId)
      principal <- checkAffordability(eligible, requested)
    } yield {
      val rate: Int = if (eligible.creditScore >= 750) 450 else 700
      Offer(eligible.id, principal, rate)
    }
  }

  @Test
  def producesPersonalizedOffersFromApplicantAndRequestRecords(): Unit = {
    assertEquals(
      Some(Right(Offer("prime", 600000L, 450))),
      offer("prime", "small").value
    )
    assertEquals(
      Some(Right(Offer("standard", 600000L, 700))),
      offer("standard", "small").value
    )
  }

  @Test
  def keepsMissingRecordsDistinctFromRejectedApplications(): Unit = {
    assertEquals(None, offer("unknown", "small").value)
    assertEquals(None, offer("prime", "unknown").value)
    assertEquals(
      Some(Left("credit score below minimum")),
      offer("declined", "small").value
    )
    assertEquals(
      Some(Left("principal exceeds income limit")),
      offer("prime", "large").value
    )
    assertEquals(
      Some(Left("principal must be positive")),
      offer("prime", "invalid").value
    )
  }

  @Test
  def preservesTheCreditRejectionWithoutLookingUpALaterMissingRequest(): Unit = {
    assertEquals(
      Some(Left("credit score below minimum")),
      offer("declined", "unknown").value
    )
    assertEquals(
      Some(Right(Offer("prime", 600000L, 450))),
      offer("prime", "small").value
    )
  }
}
