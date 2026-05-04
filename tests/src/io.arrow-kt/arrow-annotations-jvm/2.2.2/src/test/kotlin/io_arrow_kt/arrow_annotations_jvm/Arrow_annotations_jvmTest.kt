/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:arrow.synthetic

package io_arrow_kt.arrow_annotations_jvm

import arrow.optics.OpticsTarget
import arrow.optics.optics
import arrow.synthetic
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.EnumMap
import java.util.EnumSet

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
        assertThatThrownBy { OpticsTarget.valueOf("TRAVERSAL") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun opticsTargetEnumSupportsStableJdkEnumCollectionsAndOrdering() {
        val structuralTargets: EnumSet<OpticsTarget> = EnumSet.of(OpticsTarget.ISO, OpticsTarget.LENS)
        val descriptions: EnumMap<OpticsTarget, String> = EnumMap(OpticsTarget::class.java)

        OpticsTarget.entries.forEach { target: OpticsTarget ->
            descriptions[target] = target.description()
        }

        assertThat(structuralTargets).containsExactly(OpticsTarget.ISO, OpticsTarget.LENS)
        assertThat(descriptions.values)
            .containsExactly("isomorphism", "lens", "prism", "optional", "dsl")
        assertThat(OpticsTarget.entries.sortedDescending())
            .containsExactly(
                OpticsTarget.DSL,
                OpticsTarget.OPTIONAL,
                OpticsTarget.PRISM,
                OpticsTarget.LENS,
                OpticsTarget.ISO,
            )
    }

    @Test
    fun opticsAnnotationInstancesAreUsableAsConfigurationValues() {
        val defaultConfiguration: optics = optics()
        val prismDslConfiguration: optics = optics(arrayOf(OpticsTarget.PRISM, OpticsTarget.DSL))
        val equivalentConfiguration: optics = optics(arrayOf(OpticsTarget.PRISM, OpticsTarget.DSL))
        val lensConfiguration: optics = optics(arrayOf(OpticsTarget.LENS))

        assertThat(defaultConfiguration.targets).isEmpty()
        assertThat(prismDslConfiguration.targets)
            .containsExactly(OpticsTarget.PRISM, OpticsTarget.DSL)
        assertThat(prismDslConfiguration).isEqualTo(equivalentConfiguration)
        assertThat(prismDslConfiguration).isNotEqualTo(lensConfiguration)
    }

    @Test
    fun annotationInstancesCanBeUsedAsHashBasedLookupKeys() {
        val generatedDeclarationKinds: Map<Any, String> = mapOf(
            synthetic() to "synthetic declaration",
            optics.copy() to "copy optics declaration",
            optics(arrayOf(OpticsTarget.LENS, OpticsTarget.OPTIONAL)) to "focused optics declaration",
        )

        assertThat(generatedDeclarationKinds[synthetic()]).isEqualTo("synthetic declaration")
        assertThat(generatedDeclarationKinds[optics.copy()]).isEqualTo("copy optics declaration")
        assertThat(generatedDeclarationKinds[optics(arrayOf(OpticsTarget.LENS, OpticsTarget.OPTIONAL))])
            .isEqualTo("focused optics declaration")
        assertThat(generatedDeclarationKinds[optics(arrayOf(OpticsTarget.LENS))]).isNull()
    }

    @Test
    fun markerAnnotationInstancesHaveStableValueSemantics() {
        val syntheticMarker: synthetic = synthetic()
        val anotherSyntheticMarker: synthetic = synthetic()
        val copyMarker: optics.copy = optics.copy()

        assertThat(syntheticMarker).isEqualTo(anotherSyntheticMarker)
        assertThat(copyMarker).isEqualTo(optics.copy())
        assertThat(syntheticMarker).isNotEqualTo(copyMarker)
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
    fun opticsAnnotationCanRequestIsoTargetsOnInlineAndDataTypes() {
        val accountId: IsoAnnotatedAccountId = IsoAnnotatedAccountId("account-42")
        val coordinates: IsoAnnotatedCoordinates = IsoAnnotatedCoordinates(x = 3, y = 5)

        assertThat(accountId.normalized()).isEqualTo("ACCOUNT-42")
        assertThat(coordinates.copy(y = coordinates.x + coordinates.y))
            .isEqualTo(IsoAnnotatedCoordinates(x = 3, y = 8))
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

    @Test
    fun syntheticAnnotationCanMarkObjectsEnumsOperatorsAndSetterParameters() {
        SyntheticRegistry.clear()

        SyntheticRegistry += SyntheticRegistry.Entry(name = "alpha", weight = 2)
        SyntheticRegistry += SyntheticRegistry.Entry(name = "beta", weight = 3)

        assertThat(SyntheticRegistry.names()).containsExactly("alpha", "beta")
        assertThat(SyntheticMode.ENABLED.render()).isEqualTo("enabled")
    }
}

private fun OpticsTarget.description(): String = when (this) {
    OpticsTarget.ISO -> "isomorphism"
    OpticsTarget.LENS -> "lens"
    OpticsTarget.PRISM -> "prism"
    OpticsTarget.OPTIONAL -> "optional"
    OpticsTarget.DSL -> "dsl"
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

@optics(targets = [OpticsTarget.ISO])
@JvmInline
private value class IsoAnnotatedAccountId(
    val value: String,
) {
    fun normalized(): String = value.uppercase()
}

@optics(targets = [OpticsTarget.ISO])
private data class IsoAnnotatedCoordinates(
    val x: Int,
    val y: Int,
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
    @setparam:synthetic
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

@synthetic
private object SyntheticRegistry {
    private val entries: MutableList<Entry> = mutableListOf()

    @synthetic
    operator fun plusAssign(@synthetic entry: Entry) {
        entries += entry
    }

    @synthetic
    fun names(): List<@synthetic SyntheticString> = entries.map(Entry::name)

    @synthetic
    fun clear() {
        entries.clear()
    }

    @SyntheticDomainAnnotation("entry")
    data class Entry(
        @property:synthetic
        @field:synthetic
        val name: @synthetic SyntheticString,
        @property:synthetic
        val weight: Int,
    )
}

@synthetic
private enum class SyntheticMode {
    ENABLED,
    DISABLED,
    ;

    @synthetic
    fun render(): @synthetic SyntheticString = name.lowercase()
}
