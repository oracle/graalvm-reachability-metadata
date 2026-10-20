/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.Contextual
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.capturedKClass
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.descriptors.getContextualDescriptor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.overwriteWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises contextual serialization: `@Contextual` properties and type arguments resolved through
 * a `SerializersModule` at run time, generic contextual providers, built-in fallback serializers,
 * `overwriteWith` composition, contextual descriptors and `SerializersModuleCollector` dumps.
 *
 * An invoice export service shares one model across tenants and gives every tenant its own
 * module, so the same invoice is written with ISO dates and dotted decimals for one tenant and
 * with European dates and comma decimals for another, without touching the model classes.
 */
@OptIn(ExperimentalSerializationApi::class)
public class TenantInvoiceExportContextualSerializationTest {

    /** A value tagged with the system it came from; owned by an integration layer, not serializable. */
    public data class Sourced<T>(val system: String, val value: T)

    /** Writes a [Sourced] as `{system, value}`, using the serializer the plugin passes for `T`. */
    public class SourcedSerializer<T>(private val valueSerializer: KSerializer<T>) : KSerializer<Sourced<T>> {
        override val descriptor: SerialDescriptor =
            buildClassSerialDescriptor("invoice.Sourced", valueSerializer.descriptor) {
                element<String>("system")
                element("value", valueSerializer.descriptor)
            }

        override fun serialize(encoder: Encoder, value: Sourced<T>) {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, value.system)
                encodeSerializableElement(descriptor, 1, valueSerializer, value.value)
            }
        }

        override fun deserialize(decoder: Decoder): Sourced<T> = decoder.decodeStructure(descriptor) {
            var system: String? = null
            var value: T? = null
            var valueSeen = false
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    0 -> system = decodeStringElement(descriptor, 0)
                    1 -> {
                        value = decodeSerializableElement(descriptor, 1, valueSerializer)
                        valueSeen = true
                    }
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unexpected element index $index")
                }
            }
            if (system == null || !valueSeen) {
                throw SerializationException("Sourced value needs both 'system' and 'value'")
            }
            @Suppress("UNCHECKED_CAST")
            Sourced(system, value as T)
        }
    }

    /** Default tenant format: ISO-8601 dates. */
    public object IsoDateSerializer : KSerializer<LocalDate> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("invoice.IsoDate", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: LocalDate) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
    }

    /** Default tenant format: plain decimal text with a dot, so no precision is lost. */
    public object DottedDecimalSerializer : KSerializer<BigDecimal> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("invoice.DottedDecimal", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: BigDecimal) {
            encoder.encodeString(value.toPlainString())
        }

        override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
    }

    /** European tenant format: `dd.MM.yyyy` dates. */
    public object EuropeanDateSerializer : KSerializer<LocalDate> {
        private val format: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("eu.Date", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: LocalDate) {
            encoder.encodeString(value.format(format))
        }

        override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString(), format)
    }

    /** European tenant format: decimal text with a comma separator. */
    public object CommaDecimalSerializer : KSerializer<BigDecimal> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("eu.CommaDecimal", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: BigDecimal) {
            encoder.encodeString(value.toPlainString().replace('.', ','))
        }

        override fun deserialize(decoder: Decoder): BigDecimal =
            BigDecimal(decoder.decodeString().replace(',', '.'))
    }

    /** European tenant format: payment terms as a whole number of days. */
    public object PaymentTermDaysSerializer : KSerializer<Duration> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("eu.PaymentTermDays", PrimitiveKind.INT)

        override fun serialize(encoder: Encoder, value: Duration) {
            encoder.encodeInt(value.inWholeDays.toInt())
        }

        override fun deserialize(decoder: Decoder): Duration = decoder.decodeInt().days
    }

    /** An invoice whose foreign-typed fields are resolved per tenant. */
    @Serializable
    public data class Invoice(
        val number: String,
        @Contextual val issuedOn: LocalDate,
        @Contextual val total: BigDecimal,
        @Contextual val paymentTerm: Duration,
        val customerRef: @Contextual Sourced<String>,
        val lineAmounts: List<@Contextual BigDecimal>,
        @Contextual val discount: BigDecimal? = null,
    )

    /** A parcel whose weight comes from the warehouse system as a tagged integer. */
    @Serializable
    public data class Parcel(val trackingId: String, val weightGrams: @Contextual Sourced<Int>)

    /** Lists what a tenant module registers, for the export service's diagnostics page. */
    public class RegistrationReport : SerializersModuleCollector {
        public val fixed: MutableMap<String, String> = sortedMapOf()
        public val generic: MutableSet<String> = sortedSetOf()
        public val polymorphic: MutableList<String> = mutableListOf()

        override fun <T : Any> contextual(kClass: KClass<T>, serializer: KSerializer<T>) {
            fixed[kClass.simpleName!!] = serializer.descriptor.serialName
        }

        override fun <T : Any> contextual(
            kClass: KClass<T>,
            provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
        ) {
            generic += kClass.simpleName!!
        }

        override fun <Base : Any, Sub : Base> polymorphic(
            baseClass: KClass<Base>,
            actualClass: KClass<Sub>,
            actualSerializer: KSerializer<Sub>,
        ) {
            polymorphic += "${baseClass.simpleName}/${actualClass.simpleName}"
        }

        override fun <Base : Any> polymorphicDefaultSerializer(
            baseClass: KClass<Base>,
            defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?,
        ) {
            polymorphic += "${baseClass.simpleName}/default-serializer"
        }

        override fun <Base : Any> polymorphicDefaultDeserializer(
            baseClass: KClass<Base>,
            defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?,
        ) {
            polymorphic += "${baseClass.simpleName}/default-deserializer"
        }
    }

    private val baseModule: SerializersModule = SerializersModule {
        contextual(IsoDateSerializer)
        contextual(DottedDecimalSerializer)
        contextual(Sourced::class) { args -> SourcedSerializer(args[0]) }
    }

    private val europeanModule: SerializersModule = baseModule overwriteWith SerializersModule {
        contextual(EuropeanDateSerializer)
        contextual(CommaDecimalSerializer)
        contextual(PaymentTermDaysSerializer)
    }

    private val defaultTenant: Json = Json { serializersModule = baseModule }
    private val europeanTenant: Json = Json { serializersModule = europeanModule }

    private val invoice: Invoice = Invoice(
        number = "INV-2026-0042",
        issuedOn = LocalDate.of(2026, 10, 17),
        total = BigDecimal("1234.50"),
        paymentTerm = 30.days,
        customerRef = Sourced("crm", "ACME-7"),
        lineAmounts = listOf(BigDecimal("1000.00"), BigDecimal("234.50")),
        discount = BigDecimal("12.35"),
    )

    @Test
    fun defaultTenantUsesItsModuleAndFallsBackToTheBuiltInDurationSerializer() {
        val json: String = defaultTenant.encodeToString(Invoice.serializer(), invoice)

        assertEquals(
            """{"number":"INV-2026-0042","issuedOn":"2026-10-17","total":"1234.50",""" +
                """"paymentTerm":"PT720H","customerRef":{"system":"crm","value":"ACME-7"},""" +
                """"lineAmounts":["1000.00","234.50"],"discount":"12.35"}""",
            json,
        )
        assertEquals(invoice, defaultTenant.decodeFromString(Invoice.serializer(), json))
    }

    @Test
    fun europeanTenantOverridesDatesDecimalsAndPaymentTermsWithoutTouchingTheModel() {
        val json: String = europeanTenant.encodeToString(Invoice.serializer(), invoice)

        assertEquals(
            """{"number":"INV-2026-0042","issuedOn":"17.10.2026","total":"1234,50",""" +
                """"paymentTerm":30,"customerRef":{"system":"crm","value":"ACME-7"},""" +
                """"lineAmounts":["1000,00","234,50"],"discount":"12,35"}""",
            json,
        )
        assertEquals(invoice, europeanTenant.decodeFromString(Invoice.serializer(), json))
    }

    @Test
    fun nullableContextualPropertyIsOmittedAndRestoredAsItsDefault() {
        val undiscounted: Invoice = invoice.copy(discount = null)

        val json: String = europeanTenant.encodeToString(Invoice.serializer(), undiscounted)

        assertTrue(!json.contains("discount"), json)
        val restored: Invoice = europeanTenant.decodeFromString(Invoice.serializer(), json)
        assertNull(restored.discount)
        assertEquals(undiscounted, restored)
    }

    @Test
    fun genericProviderReceivesTheSerializerOfEachTypeArgument() {
        val parcel = Parcel("P-0001", Sourced("wms", 1250))

        val json: String = defaultTenant.encodeToString(Parcel.serializer(), parcel)

        assertEquals("""{"trackingId":"P-0001","weightGrams":{"system":"wms","value":1250}}""", json)
        assertEquals(parcel, defaultTenant.decodeFromString(Parcel.serializer(), json))
        assertThrows(SerializationException::class.java) {
            defaultTenant.decodeFromString(Parcel.serializer(), """{"trackingId":"P-0002","weightGrams":{"system":"wms"}}""")
        }
    }

    @Test
    fun theSameTenantModuleDrivesABinaryFormat() {
        val cbor = Cbor { serializersModule = europeanModule }

        val bytes: ByteArray = cbor.encodeToByteArray(invoice)

        assertEquals(invoice, cbor.decodeFromByteArray<Invoice>(bytes))
    }

    @Test
    fun formatWithoutARegistrationRejectsContextualTypesItCannotResolve() {
        val unconfigured: Json = Json

        val failure: SerializationException = assertThrows(SerializationException::class.java) {
            unconfigured.encodeToString(Invoice.serializer(), invoice)
        }

        assertTrue(failure.message!!.contains("LocalDate"), failure.message)
    }

    @Test
    fun schemaToolResolvesContextualDescriptorsThroughEachTenantModule() {
        val invoiceDescriptor: SerialDescriptor = Invoice.serializer().descriptor
        val issuedOn: SerialDescriptor = invoiceDescriptor.getElementDescriptor(invoiceDescriptor.getElementIndex("issuedOn"))
        val paymentTerm: SerialDescriptor =
            invoiceDescriptor.getElementDescriptor(invoiceDescriptor.getElementIndex("paymentTerm"))

        assertEquals(SerialKind.CONTEXTUAL, issuedOn.kind)
        assertEquals(LocalDate::class, issuedOn.capturedKClass)
        assertEquals(Duration::class, paymentTerm.capturedKClass)
        assertEquals("invoice.IsoDate", baseModule.getContextualDescriptor(issuedOn)?.serialName)
        assertEquals("eu.Date", europeanModule.getContextualDescriptor(issuedOn)?.serialName)
        assertNull(baseModule.getContextualDescriptor(paymentTerm))
        assertEquals(PrimitiveKind.INT, europeanModule.getContextualDescriptor(paymentTerm)?.kind)
    }

    @Test
    fun diagnosticsDumpShowsTheEffectiveRegistrationsAfterOverwrite() {
        val report = RegistrationReport()

        europeanModule.dumpTo(report)

        assertEquals(
            mapOf(
                "BigDecimal" to "eu.CommaDecimal",
                "Duration" to "eu.PaymentTermDays",
                "LocalDate" to "eu.Date",
            ),
            report.fixed,
        )
        assertEquals(setOf("Sourced"), report.generic)
        assertTrue(report.polymorphic.isEmpty(), report.polymorphic.toString())
    }
}
