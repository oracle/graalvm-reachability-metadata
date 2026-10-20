/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.Codec
import io.circe.Json
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import io.circe.parser.parse
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises recursive sum-type codec derivation for persisted promotion eligibility policies. */
@Timeout(60)
class PromotionPolicyTest {
  sealed trait Rule

  final case class MinimumSpend(amount: BigDecimal) extends Rule
  final case class CustomerTag(tag: String) extends Rule
  final case class AllOf(rules: List[Rule]) extends Rule
  final case class AnyOf(rules: List[Rule]) extends Rule

  object Rule {
    given Codec.AsObject[Rule] = deriveCodec[Rule]
  }

  final case class Promotion(code: String, rule: Rule)

  object Promotion {
    given Codec.AsObject[Promotion] = deriveCodec[Promotion]
  }

  final case class Basket(total: BigDecimal, customerTags: Set[String])

  @Test
  def restoresEvaluatesAndRevisesANestedPromotionPolicy(): Unit = {
    val stored: String = """
      {
        "code": "LOYALTY",
        "rule": {
          "AllOf": {"rules": [
            {"MinimumSpend": {"amount": 50}},
            {"AnyOf": {"rules": [
              {"CustomerTag": {"tag": "member"}},
              {"CustomerTag": {"tag": "employee"}}
            ]}}
          ]}
        }
      }
      """
    val promotion: Promotion = decode[Promotion](stored).fold(
      error => throw new AssertionError(s"Stored promotion was rejected: $error"),
      identity
    )
    assertThat(promotion).isEqualTo(Promotion("LOYALTY", AllOf(List(
      MinimumSpend(BigDecimal(50)),
      AnyOf(List(CustomerTag("member"), CustomerTag("employee")))
    ))))

    val baskets: List[Basket] = List(
      Basket(BigDecimal(50), Set("member")),
      Basket(BigDecimal(80), Set("employee")),
      Basket(BigDecimal(49), Set("member")),
      Basket(BigDecimal(80), Set.empty)
    )
    assertThat(baskets.map(qualifies(promotion.rule, _))).isEqualTo(
      List(true, true, false, false)
    )
    assertThat(promotion.asJson).isEqualTo(fixture(stored))

    // Keep the existing policy as one branch of a new customer-service override.
    val revised: Promotion = promotion.copy(rule = AnyOf(List(
      promotion.rule,
      CustomerTag("service-recovery")
    )))
    val published: Json = revised.asJson
    assertThat(published.hcursor.downField("rule").downField("AnyOf")
      .downField("rules").downArray.focus).isEqualTo(Some(promotion.rule.asJson))
    val restored: Promotion = decode[Promotion](published.noSpaces).fold(
      error => throw new AssertionError(s"Revised promotion was rejected: $error"),
      identity
    )
    assertThat(restored).isEqualTo(revised)
    assertThat(baskets.map(qualifies(restored.rule, _))).isEqualTo(
      List(true, true, false, false)
    )
    val recoveryBasket: Basket = Basket(BigDecimal(10), Set("service-recovery"))
    assertThat(qualifies(promotion.rule, recoveryBasket)).isFalse()
    assertThat(qualifies(restored.rule, recoveryBasket)).isTrue()

    val unknownNestedRule: String = stored.replace("CustomerTag", "RetiredCustomerGroup")
    assertThat(decode[Promotion](unknownNestedRule).isLeft).isTrue()
  }

  private def qualifies(rule: Rule, basket: Basket): Boolean = rule match {
    case MinimumSpend(amount) => basket.total >= amount
    case CustomerTag(tag) => basket.customerTags.contains(tag)
    case AllOf(rules) => rules.forall(qualifies(_, basket))
    case AnyOf(rules) => rules.exists(qualifies(_, basket))
  }

  private def fixture(input: String): Json = parse(input).fold(
    error => throw new AssertionError(s"Invalid promotion fixture: $error"),
    identity
  )
}
