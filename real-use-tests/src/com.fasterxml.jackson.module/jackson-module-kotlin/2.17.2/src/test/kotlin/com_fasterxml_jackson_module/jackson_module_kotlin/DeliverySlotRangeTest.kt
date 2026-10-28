/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises Kotlin closed-range JSON binding for delivery-slot configuration.
 * A booking service reads inclusive opening hours, offers hourly slots, and publishes
 * the configuration for another booking worker without exposing range implementation details.
 */
public class DeliverySlotRangeTest {

    public data class DeliverySchedule(
        val depot: String,
        val hoursByDay: Map<String, IntRange>,
    ) {
        public fun slotsFor(day: String): List<String> = hoursByDay.getValue(day).map { hour: Int ->
            "$depot|$day|${hour.toString().padStart(2, '0')}:00"
        }
    }

    @Test
    @Timeout(60)
    public fun loadsInclusiveOpeningHoursAndPublishesBookableSlots(): Unit {
        val module: KotlinModule = KotlinModule.Builder().build()
        val mapper: ObjectMapper = ObjectMapper().registerModule(module)
        val configurationJson: String = """
            {
              "depot": "north",
              "hoursByDay": {
                "weekday": {"start": 9, "end": 17},
                "saturday": {"start": 10, "end": 12}
              }
            }
        """.trimIndent()

        val schedule: DeliverySchedule = mapper.readValue(configurationJson)

        assertEquals("north", schedule.depot)
        assertEquals(9..17, schedule.hoursByDay.getValue("weekday"))
        assertEquals(10..12, schedule.hoursByDay.getValue("saturday"))
        val weekdayHours: IntRange = schedule.hoursByDay.getValue("weekday")
        assertFalse(8 in weekdayHours)
        assertTrue(9 in weekdayHours)
        assertTrue(17 in weekdayHours)
        assertFalse(18 in weekdayHours)
        val weekdaySlots: List<String> = schedule.slotsFor("weekday")
        assertEquals(9, weekdaySlots.size)
        assertEquals("north|weekday|09:00", weekdaySlots.first())
        assertEquals("north|weekday|17:00", weekdaySlots.last())
        assertEquals(
            listOf("north|saturday|10:00", "north|saturday|11:00", "north|saturday|12:00"),
            schedule.slotsFor("saturday"),
        )

        val publishedJson: String = mapper.writeValueAsString(schedule)
        val published: JsonNode = mapper.readTree(publishedJson)
        assertEquals(mapper.readTree(configurationJson), published)
        val workerSchedule: DeliverySchedule = mapper.readValue(publishedJson)
        assertEquals(schedule, workerSchedule)
        assertEquals(weekdaySlots, workerSchedule.slotsFor("weekday"))
        assertEquals(schedule.slotsFor("saturday"), workerSchedule.slotsFor("saturday"))
    }
}
