/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises inline value classes in generated serializers: `@JvmInline` value class properties
 * encoded as their underlying value, value classes wrapping nullable, collection and class types,
 * value-class map keys, inline descriptors, constructor validation on decode, and the
 * `encodeInline`/`decodeInline` hooks a custom format uses to see the wrapper type.
 *
 * A payments ledger keeps account ids and amounts in zero-cost wrappers so they cannot be mixed
 * up, sends transfers as JSON and CBOR, and writes a typed audit trail through its own format.
 */
@OptIn(ExperimentalSerializationApi::class)
public class LedgerTransferValueClassInlineTest {

    /** Ledger account identifier; rejects ids outside the ledger's namespace. */
    @Serializable
    @JvmInline
    public value class AccountId(val raw: String) {
        init {
            require(raw.startsWith("ACC-")) { "Account ids start with ACC-: $raw" }
        }
    }

    /** Monetary amount in minor units. */
    @Serializable
    @JvmInline
    public value class Cents(val amount: Long)

    /** Free-text note that may be absent while the wrapper itself is always present. */
    @Serializable
    @JvmInline
    public value class Memo(val text: String?)

    /** Labels attached to a transfer for reporting. */
    @Serializable
    @JvmInline
    public value class Tags(val labels: List<String>)

    /** Party receiving the funds. */
    @Serializable
    public data class Counterparty(val name: String, val country: String)

    /** Beneficiary details kept distinct from other counterparty roles. */
    @Serializable
    @JvmInline
    public value class Beneficiary(val party: Counterparty)

    /** A transfer between two ledger accounts. */
    @Serializable
    public data class Transfer(
        val id: Long,
        val from: AccountId,
        val to: AccountId,
        val amount: Cents,
        val fee: Cents? = null,
        val memo: Memo = Memo(null),
        val tags: Tags = Tags(emptyList()),
        val beneficiary: Beneficiary,
    )

    /** End-of-day balances keyed by account. */
    @Serializable
    public data class LedgerSnapshot(val balances: Map<AccountId, Cents>, val pending: List<Cents>)

    /** One flat audit-trail record. */
    @Serializable
    public data class AuditRecord(
        val account: AccountId,
        val amount: Cents,
        val memo: Memo,
        val attempt: Int,
        val approvedBy: AccountId? = null,
    )

    /**
     * Line-oriented audit format: one `name=value` line per property, where a value written
     * through a value class carries its wrapper type as `name:Type=value`.
     */
    public class AuditLineFormat(
        override val serializersModule: SerializersModule = SerializersModule {},
    ) : StringFormat {

        override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
            val encoder: LineEncoder = LineEncoder(serializersModule)
            encoder.encodeSerializableValue(serializer, value)
            return encoder.lines.joinToString("\n")
        }

        override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
            val entries: List<Entry> = string.lines().filter { it.isNotEmpty() }.map(::parseEntry)
            return LineDecoder(entries, serializersModule).decodeSerializableValue(deserializer)
        }

        /** A parsed audit line. */
        public data class Entry(val name: String, val type: String?, val value: String)

        private class LineEncoder(override val serializersModule: SerializersModule) : AbstractEncoder() {
            val lines: MutableList<String> = mutableListOf()
            private var depth: Int = 0
            private var name: String? = null
            private var type: String? = null

            override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
                if (depth > 0) {
                    throw SerializationException("Audit lines are flat; cannot nest ${descriptor.serialName}")
                }
                depth++
                return this
            }

            override fun endStructure(descriptor: SerialDescriptor) {
                depth--
            }

            override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
                name = descriptor.getElementName(index)
                type = null
                return true
            }

            override fun encodeInline(descriptor: SerialDescriptor): Encoder {
                type = typeTag(descriptor)
                return this
            }

            override fun encodeValue(value: Any) {
                write(value.toString())
            }

            override fun encodeNull() {
                write(NULL_MARK)
            }

            private fun write(text: String) {
                val property: String = name ?: throw SerializationException("Audit lines encode classes only")
                lines += if (type == null) "$property=$text" else "$property:$type=$text"
            }
        }

        private class LineDecoder(
            private val entries: List<Entry>,
            override val serializersModule: SerializersModule,
        ) : AbstractDecoder() {
            private var position: Int = 0
            private var confirmedType: String? = null

            override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
                if (position == entries.size) {
                    return CompositeDecoder.DECODE_DONE
                }
                confirmedType = null
                val name: String = entries[position].name
                val index: Int = descriptor.getElementIndex(name)
                if (index == CompositeDecoder.UNKNOWN_NAME) {
                    throw SerializationException("Unknown audit field '$name'")
                }
                return index
            }

            override fun decodeInline(descriptor: SerialDescriptor): Decoder {
                val entry: Entry = entries[position]
                val expected: String = typeTag(descriptor)
                if (entry.type != expected) {
                    throw SerializationException("Field '${entry.name}' is typed ${entry.type}, expected $expected")
                }
                confirmedType = expected
                return this
            }

            override fun decodeNotNullMark(): Boolean = entries[position].value != NULL_MARK

            override fun decodeNull(): Nothing? {
                take()
                return null
            }

            override fun decodeString(): String = take()

            override fun decodeLong(): Long = take().toLong()

            override fun decodeInt(): Int = take().toInt()

            private fun take(): String {
                val entry: Entry = entries[position]
                if (entry.type != confirmedType) {
                    throw SerializationException("Field '${entry.name}' is typed ${entry.type} but was read as a plain value")
                }
                position++
                return entry.value
            }
        }

        private companion object {
            const val NULL_MARK: String = "~"

            fun typeTag(descriptor: SerialDescriptor): String = descriptor.serialName.substringAfterLast('.')

            fun parseEntry(line: String): Entry {
                val separator: Int = line.indexOf('=')
                if (separator < 0) {
                    throw SerializationException("Malformed audit line '$line'")
                }
                val head: String = line.substring(0, separator)
                val value: String = line.substring(separator + 1)
                val colon: Int = head.indexOf(':')
                return if (colon < 0) {
                    Entry(head, null, value)
                } else {
                    Entry(head.substring(0, colon), head.substring(colon + 1), value)
                }
            }
        }
    }

    private val verboseJson: Json = Json { encodeDefaults = true }

    private val rentTransfer: Transfer = Transfer(
        id = 41L,
        from = AccountId("ACC-1"),
        to = AccountId("ACC-2"),
        amount = Cents(125_000L),
        fee = Cents(250L),
        memo = Memo("October rent"),
        tags = Tags(listOf("home", "monthly")),
        beneficiary = Beneficiary(Counterparty("Ada Landlord", "RS")),
    )

    @Test
    fun transferEncodesEveryValueClassAsItsUnderlyingValue() {
        val json: String = Json.encodeToString(rentTransfer)

        assertEquals(
            """{"id":41,"from":"ACC-1","to":"ACC-2","amount":125000,"fee":250,"memo":"October rent",""" +
                """"tags":["home","monthly"],"beneficiary":{"name":"Ada Landlord","country":"RS"}}""",
            json,
        )
        assertEquals(rentTransfer, Json.decodeFromString<Transfer>(json))
    }

    @Test
    fun valueClassDefaultsAndNullableWrappersFollowTheirUnderlyingValue() {
        val minimal: Transfer = Transfer(
            id = 42L,
            from = AccountId("ACC-3"),
            to = AccountId("ACC-1"),
            amount = Cents(-500L),
            beneficiary = Beneficiary(Counterparty("Refund Desk", "DE")),
        )

        assertEquals(
            """{"id":42,"from":"ACC-3","to":"ACC-1","amount":-500,"beneficiary":{"name":"Refund Desk","country":"DE"}}""",
            Json.encodeToString(minimal),
        )
        val verbose: String = verboseJson.encodeToString(minimal)
        assertTrue(verbose.contains(""""fee":null,"memo":null,"tags":[]"""), verbose)
        assertEquals(minimal, Json.decodeFromString<Transfer>(verbose))

        val withoutNote: Transfer = Json.decodeFromString(
            """{"id":43,"from":"ACC-1","to":"ACC-2","amount":1,"memo":null,"beneficiary":{"name":"X","country":"FR"}}""",
        )
        assertEquals(Memo(null), withoutNote.memo)
        assertEquals(null, withoutNote.fee)
        assertEquals(Tags(emptyList()), withoutNote.tags)
    }

    @Test
    fun standaloneValueClassesAreIndistinguishableFromTheirUnderlyingValueOnTheWire() {
        assertEquals("125000", Json.encodeToString(Cents(125_000L)))
        assertEquals(Json.encodeToString(125_000L), Json.encodeToString(Cents.serializer(), Cents(125_000L)))
        assertEquals(AccountId("ACC-7"), Json.decodeFromString<AccountId>("\"ACC-7\""))
        assertEquals(
            listOf(Cents(1L), Cents(2L)),
            Json.decodeFromString(ListSerializer(Cents.serializer()), "[1,2]"),
        )
        assertArrayEquals(Cbor.encodeToByteArray(125_000L), Cbor.encodeToByteArray(Cents(125_000L)))
        assertArrayEquals(
            Cbor.encodeToByteArray(listOf("home", "monthly")),
            Cbor.encodeToByteArray(Tags(listOf("home", "monthly"))),
        )
    }

    @Test
    fun valueClassesServeAsMapKeysAndCollectionElements() {
        val snapshot: LedgerSnapshot = LedgerSnapshot(
            balances = linkedMapOf(AccountId("ACC-1") to Cents(5_000L), AccountId("ACC-2") to Cents(-300L)),
            pending = listOf(Cents(100L), Cents(200L)),
        )

        val json: String = Json.encodeToString(snapshot)

        assertEquals("""{"balances":{"ACC-1":5000,"ACC-2":-300},"pending":[100,200]}""", json)
        val restored: LedgerSnapshot = Json.decodeFromString(json)
        assertEquals(snapshot, restored)
        assertEquals(Cents(-300L), restored.balances[AccountId("ACC-2")])
        assertEquals(snapshot, Cbor.decodeFromByteArray<LedgerSnapshot>(Cbor.encodeToByteArray(snapshot)))
    }

    @Test
    fun decodingRunsTheValueClassConstructorValidation() {
        val error: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            Json.decodeFromString<Transfer>(
                """{"id":44,"from":"EXT-9","to":"ACC-2","amount":1,"beneficiary":{"name":"X","country":"FR"}}""",
            )
        }
        assertTrue(error.message!!.contains("Account ids start with ACC-: EXT-9"), error.message)

        assertThrows(IllegalArgumentException::class.java) {
            Json.decodeFromString<LedgerSnapshot>("""{"balances":{"BAD":1},"pending":[]}""")
        }
    }

    @Test
    fun descriptorsMarkValueClassesAsInlineWrappersOfTheirUnderlyingType() {
        val cents: SerialDescriptor = Cents.serializer().descriptor
        assertTrue(cents.isInline)
        assertEquals(1, cents.elementsCount)
        assertEquals("amount", cents.getElementName(0))
        assertEquals(PrimitiveKind.LONG, cents.getElementDescriptor(0).kind)

        assertTrue(Memo.serializer().descriptor.getElementDescriptor(0).isNullable)
        assertEquals(StructureKind.LIST, Tags.serializer().descriptor.getElementDescriptor(0).kind)
        assertEquals(
            Counterparty.serializer().descriptor.serialName,
            Beneficiary.serializer().descriptor.getElementDescriptor(0).serialName,
        )

        val transfer: SerialDescriptor = Transfer.serializer().descriptor
        val amount: SerialDescriptor = transfer.getElementDescriptor(transfer.getElementIndex("amount"))
        assertTrue(amount.isInline)
        assertFalse(amount.isNullable)
        val fee: SerialDescriptor = transfer.getElementDescriptor(transfer.getElementIndex("fee"))
        assertTrue(fee.isInline)
        assertTrue(fee.isNullable)
        assertTrue(transfer.isElementOptional(transfer.getElementIndex("fee")))
        assertFalse(transfer.getElementDescriptor(transfer.getElementIndex("id")).isInline)
        assertFalse(Long.serializer().descriptor.isInline)
    }

    @Test
    fun customFormatSeesTheWrapperTypeThroughEncodeInline() {
        val format: AuditLineFormat = AuditLineFormat()
        val record: AuditRecord = AuditRecord(
            account = AccountId("ACC-1"),
            amount = Cents(125_000L),
            memo = Memo("October rent"),
            attempt = 2,
        )

        val text: String = format.encodeToString(record)

        assertEquals(
            listOf(
                "account:AccountId=ACC-1",
                "amount:Cents=125000",
                "memo:Memo=October rent",
                "attempt=2",
                "approvedBy=~",
            ),
            text.lines(),
        )
        assertEquals(record, format.decodeFromString<AuditRecord>(text))
    }

    @Test
    fun customFormatRoundTripsNullableWrappersAndWrappersOverNull() {
        val format: AuditLineFormat = AuditLineFormat()
        val record: AuditRecord = AuditRecord(
            account = AccountId("ACC-5"),
            amount = Cents(-75L),
            memo = Memo(null),
            attempt = 1,
            approvedBy = AccountId("ACC-9"),
        )

        val text: String = format.encodeToString(record)

        assertEquals(
            listOf(
                "account:AccountId=ACC-5",
                "amount:Cents=-75",
                "memo:Memo=~",
                "attempt=1",
                "approvedBy:AccountId=ACC-9",
            ),
            text.lines(),
        )
        assertEquals(record, format.decodeFromString<AuditRecord>(text))

        val legacy: AuditRecord = format.decodeFromString(
            """
            account:AccountId=ACC-5
            amount:Cents=10
            memo:Memo=migrated
            attempt=3
            """.trimIndent(),
        )
        assertEquals(null, legacy.approvedBy)
        assertEquals(Memo("migrated"), legacy.memo)
    }

    @Test
    fun customFormatRejectsValuesWhoseWrapperTypeDoesNotMatchTheSchema() {
        val format: AuditLineFormat = AuditLineFormat()
        val swapped: String = """
            account:Cents=ACC-1
            amount:Cents=1
            memo:Memo=x
            attempt=1
        """.trimIndent()
        val untyped: String = """
            account=ACC-1
            amount:Cents=1
            memo:Memo=x
            attempt=1
        """.trimIndent()
        val overTyped: String = """
            account:AccountId=ACC-1
            amount:Cents=1
            memo:Memo=x
            attempt:Cents=1
        """.trimIndent()

        val swappedError: SerializationException = assertThrows(SerializationException::class.java) {
            format.decodeFromString<AuditRecord>(swapped)
        }
        assertEquals("Field 'account' is typed Cents, expected AccountId", swappedError.message)
        val untypedError: SerializationException = assertThrows(SerializationException::class.java) {
            format.decodeFromString<AuditRecord>(untyped)
        }
        assertEquals("Field 'account' is typed null, expected AccountId", untypedError.message)
        val overTypedError: SerializationException = assertThrows(SerializationException::class.java) {
            format.decodeFromString<AuditRecord>(overTyped)
        }
        assertEquals("Field 'attempt' is typed Cents but was read as a plain value", overTypedError.message)
    }

    @Test
    fun customFormatRefusesValueClassesThatWrapStructures() {
        val format: AuditLineFormat = AuditLineFormat()

        val error: SerializationException = assertThrows(SerializationException::class.java) {
            format.encodeToString(rentTransfer)
        }
        assertTrue(error.message!!.startsWith("Audit lines are flat; cannot nest"), error.message)
    }
}
