/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises runtime serializer lookup by type: `serializer<T>()`, `serializer(KType)`,
 * `serializerOrNull`, `SerializersModule.serializer` and lookup by `java.lang.reflect.Type`.
 *
 * A workspace service keeps typed documents in an H2 table through a generic store whose
 * reified `put`/`get` resolve the serializer from the declared type, the way frameworks such
 * as Ktor and Retrofit converters do, including generic, nullable, sealed and contextual types.
 */
@OptIn(ExperimentalSerializationApi::class)
public class WorkspaceDocumentStoreSerializerLookupTest {

    /** Editor colour scheme; one constant has a custom wire name. */
    @Serializable
    public enum class Theme {
        LIGHT,
        DARK,

        @SerialName("high-contrast")
        HIGH_CONTRAST,
    }

    /** Identifier of a workspace member, inlined on the wire. */
    @Serializable
    @JvmInline
    public value class UserId(val value: String)

    /** Per-user editor preferences. */
    @Serializable
    @SerialName("editor-prefs")
    public data class EditorPrefs(
        val theme: Theme,
        val fontSize: Int,
        val pinnedTabs: List<String> = emptyList(),
    )

    /** A versioned document owned by one user, generic in its payload. */
    @Serializable
    @SerialName("envelope")
    public data class Envelope<T>(
        val owner: UserId,
        val revision: Int,
        val payload: T,
    )

    /** Notices shown to a user, a closed hierarchy. */
    @Serializable
    public sealed class Notice {
        /** A banner with text the user must dismiss. */
        @Serializable
        @SerialName("banner")
        public data class Banner(val text: String) : Notice()

        /** A marker that the user muted all notices. */
        @Serializable
        @SerialName("muted")
        public data object Muted : Notice()
    }

    /** Layout used when a user has not saved one. */
    @Serializable
    @SerialName("default-layout")
    public object DefaultLayout

    /** An amount of money; a domain type the application does not annotate. */
    public data class Money(val cents: Long, val currency: String)

    /** Writes [Money] as `"<cents> <currency>"`, registered contextually by the application. */
    public object MoneySerializer : KSerializer<Money> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("workspace.Money", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Money) {
            encoder.encodeString("${value.cents} ${value.currency}")
        }

        override fun deserialize(decoder: Decoder): Money {
            val parts: List<String> = decoder.decodeString().split(' ')
            if (parts.size != 2) {
                throw SerializationException("Malformed money value: $parts")
            }
            return Money(parts[0].toLong(), parts[1])
        }
    }

    /** A workspace budget whose limit uses the contextual [MoneySerializer]. */
    @Serializable
    @SerialName("budget")
    public data class Budget(
        val name: String,
        @Contextual val limit: Money,
    )

    /** A document row as stored: its serial type name and JSON body. */
    public data class StoredDocument(val typeName: String, val body: String)

    /** Generic document store: serializers are resolved from the requested type at runtime. */
    public class DocumentStore(private val connection: Connection, public val json: Json) {
        init {
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE documents (
                        doc_key VARCHAR(100) PRIMARY KEY,
                        type_name VARCHAR(200) NOT NULL,
                        body CLOB
                    )
                    """.trimIndent(),
                )
            }
        }

        public inline fun <reified T> put(key: String, value: T) {
            put(key, typeOf<T>(), value)
        }

        public inline fun <reified T> get(key: String): T = get(key, typeOf<T>())

        public fun put(key: String, type: KType, value: Any?) {
            val serializer: KSerializer<Any?> = serializerFor(type)
            val body: String = json.encodeToString(serializer, value)
            val sql = "MERGE INTO documents (doc_key, type_name, body) KEY (doc_key) VALUES (?, ?, ?)"
            connection.prepareStatement(sql).use { statement: PreparedStatement ->
                statement.setString(1, key)
                statement.setString(2, serializer.descriptor.serialName)
                statement.setString(3, body)
                statement.executeUpdate()
            }
        }

        @Suppress("UNCHECKED_CAST")
        public fun <T> get(key: String, type: KType): T {
            val serializer: KSerializer<Any?> = serializerFor(type)
            val stored: StoredDocument = load(key)
            if (stored.typeName != serializer.descriptor.serialName) {
                throw SerializationException(
                    "Document '$key' holds ${stored.typeName}, not ${serializer.descriptor.serialName}",
                )
            }
            return json.decodeFromString(serializer, stored.body) as T
        }

        public fun load(key: String): StoredDocument {
            val sql = "SELECT type_name, body FROM documents WHERE doc_key = ?"
            return connection.prepareStatement(sql).use { statement: PreparedStatement ->
                statement.setString(1, key)
                statement.executeQuery().use { rows: ResultSet ->
                    if (!rows.next()) {
                        throw NoSuchElementException("No document '$key'")
                    }
                    StoredDocument(rows.getString(1), rows.getString(2))
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun serializerFor(type: KType): KSerializer<Any?> =
            json.serializersModule.serializer(type) as KSerializer<Any?>
    }

    private val workspaceJson: Json = Json {
        encodeDefaults = false
        serializersModule = SerializersModule {
            contextual(MoneySerializer)
        }
    }

    private fun <R> withStore(block: (DocumentStore) -> R): R {
        val url = "jdbc:h2:mem:workspace-${System.nanoTime()};DB_CLOSE_DELAY=0"
        return DriverManager.getConnection(url, "sa", "").use { connection: Connection ->
            block(DocumentStore(connection, workspaceJson))
        }
    }

    @Test
    fun genericEnvelopesRoundTripThroughSerializersResolvedFromTheirType() {
        withStore { store: DocumentStore ->
            val prefs = Envelope(UserId("u-17"), 3, EditorPrefs(Theme.HIGH_CONTRAST, 14, listOf("main.kt")))
            store.put("prefs/u-17", prefs)

            val stored: StoredDocument = store.load("prefs/u-17")
            assertEquals("envelope", stored.typeName)
            assertEquals(
                """{"owner":"u-17","revision":3,"payload":{"theme":"high-contrast","fontSize":14,"pinnedTabs":["main.kt"]}}""",
                stored.body,
            )
            assertEquals(prefs, store.get<Envelope<EditorPrefs>>("prefs/u-17"))

            val envelopeDescriptor: SerialDescriptor = serializer<Envelope<EditorPrefs>>().descriptor
            assertEquals(listOf("owner", "revision", "payload"), envelopeDescriptor.elementNames.toList())
            assertEquals("editor-prefs", envelopeDescriptor.getElementDescriptor(2).serialName)
            assertTrue(envelopeDescriptor.getElementDescriptor(0).isInline)

            val counters = Envelope(UserId("u-17"), 1, mapOf(Theme.DARK to 2L, Theme.LIGHT to 5L))
            store.put("counters/u-17", counters)
            assertEquals(
                """{"owner":"u-17","revision":1,"payload":{"DARK":2,"LIGHT":5}}""",
                store.load("counters/u-17").body,
            )
            assertEquals(counters, store.get<Envelope<Map<Theme, Long>>>("counters/u-17"))

            val wrongType: SerializationException = assertThrows(SerializationException::class.java) {
                store.get<Budget>("prefs/u-17")
            }
            assertEquals("Document 'prefs/u-17' holds envelope, not budget", wrongType.message)
        }
    }

    @Test
    fun sealedObjectAndNullableDocumentsResolveTheirOwnSerializers() {
        withStore { store: DocumentStore ->
            val inbox: Map<UserId, List<Notice>> = mapOf(
                UserId("u-1") to listOf(Notice.Banner("Quota at 90%"), Notice.Muted),
                UserId("u-2") to emptyList(),
            )
            store.put("inbox", inbox)
            assertEquals(
                """{"u-1":[{"type":"banner","text":"Quota at 90%"},{"type":"muted"}],"u-2":[]}""",
                store.load("inbox").body,
            )
            val restored: Map<UserId, List<Notice>> = store.get("inbox")
            assertEquals(inbox, restored)
            assertSame(Notice.Muted, restored.getValue(UserId("u-1"))[1])

            store.put("layout", DefaultLayout)
            assertEquals(StoredDocument("default-layout", "{}"), store.load("layout"))
            assertSame(DefaultLayout, store.get<DefaultLayout>("layout"))

            store.put<Theme?>("theme/u-2", null)
            assertEquals("null", store.load("theme/u-2").body)
            assertNull(store.get<Theme?>("theme/u-2"))
            store.put<Theme?>("theme/u-2", Theme.DARK)
            assertEquals(Theme.DARK, store.get<Theme?>("theme/u-2"))

            val nullableTheme: SerialDescriptor = serializer(typeOf<Theme?>()).descriptor
            assertTrue(nullableTheme.isNullable)
            assertEquals(SerialKind.ENUM, nullableTheme.kind)
            assertEquals("high-contrast", nullableTheme.getElementName(2))
            assertEquals(StructureKind.OBJECT, serializer<DefaultLayout>().descriptor.kind)
        }
    }

    @Test
    fun contextualTypesResolveOnlyThroughTheApplicationModule() {
        withStore { store: DocumentStore ->
            val budgets: List<Budget> = listOf(
                Budget("cloud", Money(125_000, "EUR")),
                Budget("travel", Money(9_900, "USD")),
            )
            store.put("budgets", budgets)
            assertEquals(
                """[{"name":"cloud","limit":"125000 EUR"},{"name":"travel","limit":"9900 USD"}]""",
                store.load("budgets").body,
            )
            assertEquals(budgets, store.get<List<Budget>>("budgets"))

            store.put("reserve", Money(50_000, "CHF"))
            assertEquals(StoredDocument("workspace.Money", "\"50000 CHF\""), store.load("reserve"))
            assertEquals(Money(50_000, "CHF"), store.get<Money>("reserve"))
        }

        assertSame(MoneySerializer, workspaceJson.serializersModule.serializer<Money>())
        assertNull(serializerOrNull(typeOf<Money>()))
        assertThrows(SerializationException::class.java) { serializer<Money>() }
        assertNotNull(serializerOrNull(typeOf<Budget>()))
    }

    @Test
    fun converterStyleLookupByJavaTypeMatchesTheKotlinTypeLookup() {
        val byJavaType: KSerializer<Any> = workspaceJson.serializersModule.serializer(EditorPrefs::class.java)
        val byKotlinType: KSerializer<EditorPrefs> = workspaceJson.serializersModule.serializer<EditorPrefs>()
        assertEquals(byKotlinType.descriptor, byJavaType.descriptor)

        val body: String = workspaceJson.encodeToString(byJavaType, EditorPrefs(Theme.LIGHT, 12))
        assertEquals("""{"theme":"LIGHT","fontSize":12}""", body)
        assertEquals(
            EditorPrefs(Theme.LIGHT, 12),
            workspaceJson.decodeFromString(byKotlinType, """{"fontSize":12,"theme":"LIGHT"}"""),
        )
    }
}
