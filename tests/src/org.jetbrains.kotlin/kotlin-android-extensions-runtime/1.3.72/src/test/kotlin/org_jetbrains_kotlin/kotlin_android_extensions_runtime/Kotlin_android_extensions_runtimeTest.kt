/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_android_extensions_runtime

import android.view.View
import kotlinx.android.extensions.CacheImplementation
import kotlinx.android.extensions.ContainerOptions
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parceler
import kotlinx.android.parcel.Parcelize
import kotlinx.android.parcel.RawValue
import kotlinx.android.parcel.TypeParceler
import kotlinx.android.parcel.WriteWith
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class Kotlin_android_extensions_runtimeTest {
    @Test
    public fun cacheImplementationExposesStableEnumConstantsAndDefault() {
        assertThat(CacheImplementation.values().toList()).containsExactly(
            CacheImplementation.SPARSE_ARRAY,
            CacheImplementation.HASH_MAP,
            CacheImplementation.NO_CACHE
        )
        assertThat(CacheImplementation.valueOf("SPARSE_ARRAY")).isSameAs(CacheImplementation.SPARSE_ARRAY)
        assertThat(CacheImplementation.valueOf("HASH_MAP")).isSameAs(CacheImplementation.HASH_MAP)
        assertThat(CacheImplementation.valueOf("NO_CACHE")).isSameAs(CacheImplementation.NO_CACHE)
        assertThat(CacheImplementation.DEFAULT).isSameAs(CacheImplementation.HASH_MAP)
    }

    @Test
    public fun cacheImplementationValuesReturnsDefensiveCopy() {
        val values: Array<CacheImplementation> = CacheImplementation.values()
        values[0] = CacheImplementation.NO_CACHE

        assertThat(CacheImplementation.values().toList()).containsExactly(
            CacheImplementation.SPARSE_ARRAY,
            CacheImplementation.HASH_MAP,
            CacheImplementation.NO_CACHE
        )
    }

    @Test
    public fun containerOptionsAnnotationInstancesExposeDefaultAndCustomCachePolicies() {
        val defaultOptions: ContainerOptions = ContainerOptions()
        val explicitDefaultOptions: ContainerOptions = ContainerOptions(cache = CacheImplementation.HASH_MAP)
        val sparseArrayOptions: ContainerOptions = ContainerOptions(cache = CacheImplementation.SPARSE_ARRAY)
        val noCacheOptions: ContainerOptions = ContainerOptions(cache = CacheImplementation.NO_CACHE)

        assertThat(defaultOptions.cache).isSameAs(CacheImplementation.HASH_MAP)
        assertThat(explicitDefaultOptions.cache).isSameAs(CacheImplementation.HASH_MAP)
        assertThat(sparseArrayOptions.cache).isSameAs(CacheImplementation.SPARSE_ARRAY)
        assertThat(noCacheOptions.cache).isSameAs(CacheImplementation.NO_CACHE)
        assertThat(defaultOptions).isEqualTo(explicitDefaultOptions)
        assertThat(defaultOptions).isNotEqualTo(sparseArrayOptions)
        assertThat(sparseArrayOptions).isNotEqualTo(noCacheOptions)
    }

    @Test
    public fun parcelMarkerAnnotationsCanBeCreatedAndComparedAsValueObjects() {
        assertThat(Parcelize()).isEqualTo(Parcelize())
        assertThat(IgnoredOnParcel()).isEqualTo(IgnoredOnParcel())
        assertThat(RawValue()).isEqualTo(RawValue())
    }

    @Test
    public fun parcelerTypeAnnotationsPreserveTheirGenericContractsAtCompileTime() {
        val typeParceler: TypeParceler<String, Parceler<String>> = TypeParceler()
        val sameTypeParceler: TypeParceler<String, Parceler<String>> = TypeParceler()
        val writeWith: WriteWith<Parceler<*>> = WriteWith()
        val sameWriteWith: WriteWith<Parceler<*>> = WriteWith()

        assertThat(typeParceler).isEqualTo(sameTypeParceler)
        assertThat(writeWith).isEqualTo(sameWriteWith)
    }

    @Test
    public fun repeatedTypeParcelersCanBeAppliedToTheSameParcelizedType() {
        val repeatedTypeParcelerCarrier: RepeatedTypeParcelerCarrier = RepeatedTypeParcelerCarrier(
            count = 2,
            total = 4L
        )

        assertThat(repeatedTypeParcelerCarrier.count).isEqualTo(2)
        assertThat(repeatedTypeParcelerCarrier.total).isEqualTo(4L)
    }

    @Test
    public fun layoutContainerImplementationsCanExposeNullableContainerViews() {
        val container: LayoutContainer = EmptyLayoutContainer
        val containerView: View? = container.containerView

        assertThat(containerView).isNull()
    }

    @Test
    public fun annotatedDomainTypesRemainUsableAsRegularKotlinObjects() {
        val cacheCarrier: CachePolicyCarrier = CachePolicyCarrier("hash-map-cache")
        val parcelCarrier: ParcelAnnotationCarrier = ParcelAnnotationCarrier(raw = mapOf("answer" to 42))
        val parcelizedMarker: ParcelizedMarker = ParcelizedMarker("generated-id")

        assertThat(cacheCarrier.name).isEqualTo("hash-map-cache")
        assertThat(parcelCarrier.raw).isEqualTo(mapOf("answer" to 42))
        assertThat(parcelCarrier.ignoredDerivedValue).isEqualTo("not parceled")
        assertThat(parcelizedMarker.id).isEqualTo("generated-id")
    }
}

@Parcelize
@TypeParceler<Int, Parceler<Int>>
@TypeParceler<Long, Parceler<Long>>
private data class RepeatedTypeParcelerCarrier(
    val count: Int,
    val total: Long
)

@ContainerOptions(cache = CacheImplementation.SPARSE_ARRAY)
private data class CachePolicyCarrier(val name: String)

private data class ParcelAnnotationCarrier(
    val raw: @RawValue Any,
    @IgnoredOnParcel val ignoredDerivedValue: String = "not parceled"
)

@Parcelize
@TypeParceler<String, Parceler<String>>
private data class ParcelizedMarker(
    val id: @WriteWith<Parceler<*>> String
)

private object EmptyLayoutContainer : LayoutContainer {
    override val containerView: View? = null
}
