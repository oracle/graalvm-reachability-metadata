/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi_kotlin

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises wide-constructor and type-argument binding in `KotlinJsonAdapterFactory`: a data
 * class with more than 32 defaulted constructor parameters, so defaults span several
 * constructor masks, nullable type arguments in lists and maps, `Set`, `MutableCollection`,
 * `MutableList`, and `MutableMap` properties, declaration-site and use-site variance, and
 * star-projected maps.
 *
 * A mobile app syncs its per-account settings with a backend: the server sends sparse
 * documents that only name the settings a user changed, and the app pushes back a page of
 * device snapshots with partially filled sensor readings.
 */
@OptIn(ExperimentalStdlibApi::class)
public class MobileSettingsSyncWideConstructorTypeArgumentTest {

    /** Colour scheme the app renders with. */
    public enum class Theme {
        @Json(name = "system")
        SYSTEM,

        @Json(name = "light")
        LIGHT,

        @Json(name = "dark")
        DARK,
    }

    /** Every user-facing setting; all but the account id and device name have defaults. */
    public data class ClientSettings(
        val theme: Theme = Theme.SYSTEM,
        val language: String = "en",
        val region: String = "US",
        val timezone: String = "UTC",
        val fontScale: Double = 1.0,
        val highContrast: Boolean = false,
        val reduceMotion: Boolean = false,
        val notificationsEnabled: Boolean = true,
        val emailDigest: String = "weekly",
        val pushMarketing: Boolean = false,
        val pushSecurity: Boolean = true,
        val soundEnabled: Boolean = true,
        val vibrationEnabled: Boolean = true,
        val quietHoursStart: Int = 22,
        val quietHoursEnd: Int = 7,
        val autoplayVideo: Boolean = true,
        val dataSaver: Boolean = false,
        val wifiOnlyDownloads: Boolean = true,
        val downloadQuality: String = "high",
        val streamQuality: String = "auto",
        val cacheLimitMb: Int = 512,
        val offlineMode: Boolean = false,
        val analyticsOptIn: Boolean = false,
        val crashReportsOptIn: Boolean = true,
        val personalizedAds: Boolean = false,
        val locationSharing: Boolean = false,
        val contactsSync: Boolean = false,
        val calendarSync: Boolean = false,
        val biometricLogin: Boolean = false,
        val sessionTimeoutMinutes: Int = 30,
        val twoFactor: Boolean = false,
        val backupEnabled: Boolean = true,
        @Json(name = "account_id") val accountId: String,
        val backupFrequencyHours: Int = 24,
        val betaFeatures: Set<String> = emptySet(),
        val developerMode: Boolean = false,
        val deviceName: String?,
        val lastSyncedAt: Long? = null,
        val pinnedShortcuts: MutableList<String> = mutableListOf(),
    )

    /** One labelled sensor reading; a missing value means the sensor did not respond. */
    public data class Reading(val sensor: String, val value: Double?)

    /** Snapshot of one device; type arguments carry their own nullability. */
    public data class DeviceSnapshot(
        val model: String,
        val nicknames: List<String?>,
        val batteryByHour: Map<String, Int?>,
        val readings: MutableCollection<out Reading>,
        val counters: MutableMap<String, Long>,
        val extras: Map<String, *>,
    )

    /** Server page wrapper; `out T` lets a page of snapshots be read as a page of anything. */
    public data class Page<out T>(val items: List<T>, val next: String?)

    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val settingsAdapter: JsonAdapter<ClientSettings> = moshi.adapter<ClientSettings>()

    @Test
    fun sparseDocumentFillsDefaultsOnBothSidesOfTheThirtySecondParameter() {
        val json: String = """
            {
              "account_id": "acct-7",
              "theme": "dark",
              "quietHoursStart": 23,
              "dataSaver": true,
              "backupFrequencyHours": 6,
              "betaFeatures": ["new-feed", "voice-notes", "new-feed"],
              "deviceName": null,
              "pinnedShortcuts": ["scan"]
            }
        """.trimIndent()

        val settings: ClientSettings = settingsAdapter.fromJson(json)!!

        assertEquals("acct-7", settings.accountId)
        assertEquals(Theme.DARK, settings.theme)
        assertEquals(23, settings.quietHoursStart)
        assertEquals(7, settings.quietHoursEnd)
        assertTrue(settings.dataSaver)
        assertEquals(6, settings.backupFrequencyHours)
        assertEquals(setOf("new-feed", "voice-notes"), settings.betaFeatures)
        assertNull(settings.deviceName)
        assertNull(settings.lastSyncedAt)
        assertFalse(settings.developerMode)
        assertTrue(settings.backupEnabled)
        assertEquals(30, settings.sessionTimeoutMinutes)
        assertEquals(
            ClientSettings(
                accountId = "acct-7",
                deviceName = null,
                theme = Theme.DARK,
                quietHoursStart = 23,
                dataSaver = true,
                backupFrequencyHours = 6,
                betaFeatures = setOf("new-feed", "voice-notes"),
                pinnedShortcuts = mutableListOf("scan"),
            ),
            settings,
        )

        settings.pinnedShortcuts.add("timer")
        assertEquals(listOf("scan", "timer"), settings.pinnedShortcuts)
    }

    @Test
    fun absentNullableParameterWithoutDefaultBecomesNull() {
        val settings: ClientSettings = settingsAdapter.fromJson("""{"account_id": "acct-1"}""")!!

        assertEquals(ClientSettings(accountId = "acct-1", deviceName = null), settings)
    }

    @Test
    fun everySettingSurvivesARoundTripThroughTheBackend() {
        val original: ClientSettings = ClientSettings(
            theme = Theme.LIGHT,
            language = "sr",
            region = "RS",
            timezone = "Europe/Belgrade",
            fontScale = 1.25,
            highContrast = true,
            reduceMotion = true,
            notificationsEnabled = false,
            emailDigest = "daily",
            pushMarketing = true,
            pushSecurity = false,
            soundEnabled = false,
            vibrationEnabled = false,
            quietHoursStart = 21,
            quietHoursEnd = 6,
            autoplayVideo = false,
            dataSaver = true,
            wifiOnlyDownloads = false,
            downloadQuality = "low",
            streamQuality = "sd",
            cacheLimitMb = 128,
            offlineMode = true,
            analyticsOptIn = true,
            crashReportsOptIn = false,
            personalizedAds = true,
            locationSharing = true,
            contactsSync = true,
            calendarSync = true,
            biometricLogin = true,
            sessionTimeoutMinutes = 5,
            twoFactor = true,
            backupEnabled = false,
            accountId = "acct-42",
            backupFrequencyHours = 1,
            betaFeatures = linkedSetOf("dual-sim", "widgets"),
            developerMode = true,
            deviceName = "Pixel of Ana",
            lastSyncedAt = 1_760_000_000_000L,
            pinnedShortcuts = mutableListOf("camera", "notes"),
        )

        val json: String = settingsAdapter.toJson(original)
        val fields: Map<String, Any?> = moshi.adapter<Map<String, Any?>>().fromJson(json)!!

        assertEquals(39, fields.size)
        assertEquals("acct-42", fields["account_id"])
        assertFalse(fields.containsKey("accountId"))
        assertEquals("light", fields["theme"])
        assertEquals(listOf("dual-sim", "widgets"), fields["betaFeatures"])
        assertEquals(1.76e12, fields["lastSyncedAt"])
        assertEquals(original, settingsAdapter.fromJson(json))
    }

    @Test
    fun missingRequiredParameterPastTheFirstMaskIsReportedByName() {
        val failure: JsonDataException = assertThrows(JsonDataException::class.java) {
            settingsAdapter.fromJson("""{"theme": "dark", "deviceName": "tablet"}""")
        }

        assertEquals("Required value 'accountId' (JSON name 'account_id') missing at \$", failure.message)
    }

    @Test
    fun nullableTypeArgumentsKeepNullsAndInterfaceCollectionsDecode() {
        val json: String = """
            {
              "model": "SM-X900",
              "nicknames": ["kitchen tablet", null, "wall"],
              "batteryByHour": {"08": 91, "09": null, "10": 87},
              "readings": [{"sensor": "lux", "value": 412.5}, {"sensor": "temp", "value": null}],
              "counters": {"launches": 12},
              "extras": {"rooted": false, "tags": ["kiosk"], "owner": null}
            }
        """.trimIndent()
        val adapter: JsonAdapter<DeviceSnapshot> = moshi.adapter<DeviceSnapshot>()

        val snapshot: DeviceSnapshot = adapter.fromJson(json)!!

        assertEquals(listOf("kitchen tablet", null, "wall"), snapshot.nicknames)
        assertEquals(mapOf("08" to 91, "09" to null, "10" to 87), snapshot.batteryByHour)
        assertTrue(snapshot.batteryByHour.containsKey("09"))
        assertEquals(listOf(Reading("lux", 412.5), Reading("temp", null)), snapshot.readings.toList())
        assertEquals(mapOf("rooted" to false, "tags" to listOf("kiosk"), "owner" to null), snapshot.extras)

        snapshot.counters["launches"] = snapshot.counters.getValue("launches") + 1
        snapshot.counters["crashes"] = 0L
        assertEquals(mapOf("launches" to 13L, "crashes" to 0L), snapshot.counters)
    }

    @Test
    fun encodingWritesNullListElementsButDropsNullMapValuesUnlessAsked() {
        val snapshot: DeviceSnapshot = DeviceSnapshot(
            model = "SM-X900",
            nicknames = listOf(null, "wall"),
            batteryByHour = linkedMapOf("08" to 91, "09" to null),
            readings = mutableListOf(Reading("temp", null)),
            counters = mutableMapOf("launches" to 3L),
            extras = mapOf("owner" to null),
        )
        val adapter: JsonAdapter<DeviceSnapshot> = moshi.adapter<DeviceSnapshot>()

        assertEquals(
            """{"model":"SM-X900","nicknames":[null,"wall"],"batteryByHour":{"08":91},""" +
                """"readings":[{"sensor":"temp"}],"counters":{"launches":3},"extras":{}}""",
            adapter.toJson(snapshot),
        )
        assertEquals(
            """{"model":"SM-X900","nicknames":[null,"wall"],"batteryByHour":{"08":91,"09":null},""" +
                """"readings":[{"sensor":"temp","value":null}],"counters":{"launches":3},""" +
                """"extras":{"owner":null}}""",
            adapter.serializeNulls().toJson(snapshot),
        )
    }

    @Test
    fun covariantPageOfSnapshotsDecodesAndIsUsableAsAPageOfAnything() {
        val json: String = """
            {
              "items": [
                {
                  "model": "SM-X900",
                  "nicknames": [],
                  "batteryByHour": {},
                  "readings": [],
                  "counters": {},
                  "extras": {}
                }
              ],
              "next": "cursor-2"
            }
        """.trimIndent()
        val adapter: JsonAdapter<Page<DeviceSnapshot>> = moshi.adapter<Page<DeviceSnapshot>>()

        val page: Page<DeviceSnapshot> = adapter.fromJson(json)!!
        val anyPage: Page<Any> = page

        assertEquals("cursor-2", anyPage.next)
        assertEquals("SM-X900", page.items.single().model)
        assertTrue(page.items.single().readings.isEmpty())
        assertEquals(
            """{"items":[{"model":"SM-X900","nicknames":[],"batteryByHour":{},"readings":[],""" +
                """"counters":{},"extras":{}}],"next":"cursor-2"}""",
            adapter.toJson(page),
        )
        assertEquals(page, adapter.fromJson(adapter.toJson(page)))
    }
}
