/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_annotations_jvm

import arrow.optics.OpticsTarget
import arrow.optics.optics
import arrow.synthetic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class ArrowAnnotationsJvmTest {
    @Test
    fun opticsTargetEntriesExposeEveryGenerationModeInDeclarationOrder() {
        val targets: List<OpticsTarget> = OpticsTarget.entries.toList()

        assertThat(targets)
            .containsExactly(
                OpticsTarget.ISO,
                OpticsTarget.LENS,
                OpticsTarget.PRISM,
                OpticsTarget.OPTIONAL,
                OpticsTarget.DSL,
            )
        assertThat(OpticsTarget.valueOf("LENS")).isSameAs(OpticsTarget.LENS)
        assertThat(OpticsTarget.values().map(OpticsTarget::name))
            .containsExactly("ISO", "LENS", "PRISM", "OPTIONAL", "DSL")
    }

    @Test
    fun opticsAnnotationCanRequestSpecificTargetsOnDomainTypes() {
        val shippingAddress: ShippingAddress = ShippingAddress(street = "Main Street", city = "Zagreb")
        val order: OpticsAnnotatedOrder = OpticsAnnotatedOrder(id = "order-1", shippingAddress = shippingAddress)

        val movedOrder: OpticsAnnotatedOrder = order.copy(
            shippingAddress = shippingAddress.copy(city = "Belgrade"),
        )

        assertThat(movedOrder.id).isEqualTo("order-1")
        assertThat(movedOrder.shippingAddress.city).isEqualTo("Belgrade")
        assertThat(movedOrder.shippingAddress.street).isEqualTo("Main Street")
    }

    @Test
    fun opticsCopyAnnotationCanBeAppliedIndependentlyFromOtherTargets() {
        val pricing: CopyAnnotatedPricing = CopyAnnotatedPricing(amount = 19.95, currency = "EUR")

        assertThat(pricing.copy(amount = 24.50))
            .isEqualTo(CopyAnnotatedPricing(amount = 24.50, currency = "EUR"))
    }

    @Test
    fun opticsAnnotationCanUseDefaultTargetsOnSealedDomainModels() {
        val authorized: DefaultOpticsPayment = DefaultOpticsPayment.Authorized(reference = "auth-1")
        val rejected: DefaultOpticsPayment = DefaultOpticsPayment.Rejected(reason = "insufficient funds")

        assertThat(authorized.status()).isEqualTo("authorized:auth-1")
        assertThat(rejected.status()).isEqualTo("rejected:insufficient funds")
    }

    @Test
    fun syntheticAnnotationCanDecorateKotlinDeclarationsWithoutChangingBehavior() {
        val sample: SyntheticAnnotatedSample = SyntheticAnnotatedSample("payload")
        sample.mutableValue = "updated"

        val transformed: SyntheticString = sample.transform(prefix = "value")

        assertThat(sample.constructorValue).isEqualTo("payload")
        assertThat(sample.mutableValue).isEqualTo("updated")
        assertThat(transformed).isEqualTo("value:updated")
    }

    @Test
    fun syntheticAnnotationSupportsAnnotationClassesAndTypeParametersInGenericApis() {
        val numbers: SyntheticTypedBox<List<Int>> = SyntheticTypedBox(listOf(1, 2, 3))

        val doubled: SyntheticTypedBox<String> = numbers.map { values: List<Int> ->
            values.joinToString(prefix = "doubled=") { value: Int -> (value * 2).toString() }
        }

        assertThat(numbers.value).containsExactly(1, 2, 3)
        assertThat(doubled.value).isEqualTo("doubled=2, 4, 6")
    }
}

@optics(targets = [OpticsTarget.LENS, OpticsTarget.PRISM, OpticsTarget.OPTIONAL, OpticsTarget.DSL])
private data class OpticsAnnotatedOrder(
    val id: String,
    val shippingAddress: ShippingAddress,
)

@optics(targets = [OpticsTarget.LENS])
private data class ShippingAddress(
    val street: String,
    val city: String,
)

@optics.copy
private data class CopyAnnotatedPricing(
    val amount: Double,
    val currency: String,
)

@optics
private sealed interface DefaultOpticsPayment {
    fun status(): String = when (this) {
        is Authorized -> "authorized:$reference"
        is Rejected -> "rejected:$reason"
    }

    data class Authorized(val reference: String) : DefaultOpticsPayment

    data class Rejected(val reason: String) : DefaultOpticsPayment
}

@synthetic
private class SyntheticAnnotatedSample @synthetic constructor(
    @synthetic val constructorValue: @synthetic SyntheticString,
) {
    @synthetic
    @field:synthetic
    @get:synthetic
    @set:synthetic
    var mutableValue: @synthetic SyntheticString = constructorValue

    @synthetic
    fun transform(@synthetic prefix: @synthetic SyntheticString): @synthetic SyntheticString {
        @synthetic val localValue: @synthetic SyntheticString = mutableValue
        return "$prefix:$localValue"
    }
}

@synthetic
private typealias SyntheticString = String

@synthetic
private annotation class SyntheticDomainAnnotation(val name: String)

@SyntheticDomainAnnotation("box")
private data class SyntheticTypedBox<@synthetic T : Any>(
    val value: T,
) {
    fun <@synthetic R : Any> map(
        transform: (T) -> R,
    ): SyntheticTypedBox<R> = SyntheticTypedBox(transform(value))
}
