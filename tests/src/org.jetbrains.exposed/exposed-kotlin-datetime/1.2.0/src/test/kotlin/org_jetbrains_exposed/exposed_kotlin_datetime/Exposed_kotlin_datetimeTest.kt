/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_kotlin_datetime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Function
import org.jetbrains.exposed.v1.core.LiteralOp
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.CustomDateFunction
import org.jetbrains.exposed.v1.datetime.CustomDateTimeFunction
import org.jetbrains.exposed.v1.datetime.CustomDurationFunction
import org.jetbrains.exposed.v1.datetime.CustomTimeFunction
import org.jetbrains.exposed.v1.datetime.CustomTimeStampFunction
import org.jetbrains.exposed.v1.datetime.CustomTimestampWithTimeZoneFunction
import org.jetbrains.exposed.v1.datetime.Date
import org.jetbrains.exposed.v1.datetime.KotlinDurationColumnType
import org.jetbrains.exposed.v1.datetime.KotlinInstantColumnType
import org.jetbrains.exposed.v1.datetime.KotlinLocalDateColumnType
import org.jetbrains.exposed.v1.datetime.KotlinLocalDateTimeColumnType
import org.jetbrains.exposed.v1.datetime.KotlinLocalTimeColumnType
import org.jetbrains.exposed.v1.datetime.KotlinOffsetDateTimeColumnType
import org.jetbrains.exposed.v1.datetime.Month
import org.jetbrains.exposed.v1.datetime.Time
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.dateLiteral
import org.jetbrains.exposed.v1.datetime.dateParam
import org.jetbrains.exposed.v1.datetime.dateTimeLiteral
import org.jetbrains.exposed.v1.datetime.dateTimeParam
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.datetime.day
import org.jetbrains.exposed.v1.datetime.duration
import org.jetbrains.exposed.v1.datetime.durationLiteral
import org.jetbrains.exposed.v1.datetime.durationParam
import org.jetbrains.exposed.v1.datetime.hour
import org.jetbrains.exposed.v1.datetime.minute
import org.jetbrains.exposed.v1.datetime.month
import org.jetbrains.exposed.v1.datetime.second
import org.jetbrains.exposed.v1.datetime.time
import org.jetbrains.exposed.v1.datetime.timeLiteral
import org.jetbrains.exposed.v1.datetime.timeParam
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.datetime.timestampLiteral
import org.jetbrains.exposed.v1.datetime.timestampParam
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZoneLiteral
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZoneParam
import org.jetbrains.exposed.v1.datetime.year
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import java.time.Instant as JavaInstant

@OptIn(ExperimentalTime::class)
public class Exposed_kotlin_datetimeTest {
    @Test
    fun tableColumnFactoriesRegisterAllKotlinDatetimeColumnTypes() {
        val columns = DateTimeTable.columns.associateBy { it.name }

        assertThat(columns.keys).containsExactlyInAnyOrder(
            "local_date",
            "local_date_time",
            "local_time",
            "instant_value",
            "offset_date_time",
            "duration_value"
        )
        assertThat(DateTimeTable.localDate.table).isSameAs(DateTimeTable)
        assertThat(DateTimeTable.localDateTime.table).isSameAs(DateTimeTable)
        assertThat(DateTimeTable.localTime.table).isSameAs(DateTimeTable)
        assertThat(DateTimeTable.instant.table).isSameAs(DateTimeTable)
        assertThat(DateTimeTable.offsetDateTime.table).isSameAs(DateTimeTable)
        assertThat(DateTimeTable.duration.table).isSameAs(DateTimeTable)

        assertKotlinLocalDateType(DateTimeTable.localDate.columnType)
        assertKotlinLocalDateTimeType(DateTimeTable.localDateTime.columnType)
        assertKotlinLocalTimeType(DateTimeTable.localTime.columnType)
        assertKotlinInstantType(DateTimeTable.instant.columnType)
        assertKotlinOffsetDateTimeType(DateTimeTable.offsetDateTime.columnType)
        assertKotlinDurationType(DateTimeTable.duration.columnType)
    }

    @Test
    fun columnTypesConvertNativeKotlinValuesAndDatabaseValues() {
        val date = LocalDate(2024, 2, 29)
        val dateType = assertKotlinLocalDateType(DateTimeTable.localDate.columnType)
        assertThat(dateType.toLocalDate(date)).isEqualTo(date)
        assertThat(dateType.fromLocalDate(date)).isEqualTo(date)
        assertThat(dateType.valueFromDB("2024-02-29")).isEqualTo(date)

        val dateTime = LocalDateTime(2024, 2, 29, 12, 34, 56, 987_654_321)
        val dateTimeType = assertKotlinLocalDateTimeType(DateTimeTable.localDateTime.columnType)
        assertThat(dateTimeType.toLocalDateTime(dateTime)).isEqualTo(dateTime)
        assertThat(dateTimeType.fromLocalDateTime(dateTime)).isEqualTo(dateTime)
        assertThat(dateTimeType.valueFromDB(dateTime)).isEqualTo(dateTime)
        assertThat(dateTimeType.valueFromDB("2024-02-29T12:34:56.987654321")).isEqualTo(dateTime)

        val time = LocalTime(23, 45, 12, 345_678_901)
        val timeType = assertKotlinLocalTimeType(DateTimeTable.localTime.columnType)
        assertThat(timeType.toLocalTime(time)).isEqualTo(time)
        assertThat(timeType.fromLocalTime(time)).isEqualTo(time)
        assertThat(timeType.valueFromDB(time)).isEqualTo(time)
        assertThat(timeType.valueFromDB("23:45:12.345678901")).isEqualTo(time)
    }

    @Test
    fun instantOffsetDateTimeAndDurationColumnTypesConvertDatabaseValues() {
        val instant = Instant.parse("2024-02-29T12:34:56.987654321Z")
        val instantType = assertKotlinInstantType(DateTimeTable.instant.columnType)
        assertThat(instantType.toInstant(instant)).isEqualTo(instant)
        assertThat(instantType.fromInstant(instant)).isEqualTo(instant)
        assertThat(instantType.valueFromDB("2024-02-29T12:34:56.987654321Z")).isEqualTo(instant)
        assertThat(instantType.valueFromDB(Timestamp.from(JavaInstant.parse("2024-02-29T12:34:56.987654321Z"))))
            .isEqualTo(instant)

        val offsetDateTime = OffsetDateTime.of(2024, 2, 29, 12, 34, 56, 987_654_321, ZoneOffset.ofHours(2))
        val offsetDateTimeType = assertKotlinOffsetDateTimeType(DateTimeTable.offsetDateTime.columnType)
        assertThat(offsetDateTimeType.toOffsetDateTime(offsetDateTime)).isEqualTo(offsetDateTime)
        assertThat(offsetDateTimeType.fromOffsetDateTime(offsetDateTime)).isEqualTo(offsetDateTime)
        assertThat(offsetDateTimeType.valueFromDB(offsetDateTime)).isEqualTo(offsetDateTime)
        assertThat(offsetDateTimeType.valueFromDB(offsetDateTime.toZonedDateTime())).isEqualTo(offsetDateTime)

        val duration = 12.seconds + 345.nanoseconds
        val durationType = assertKotlinDurationType(DateTimeTable.duration.columnType)
        assertThat(durationType.toDuration(duration)).isEqualTo(duration)
        assertThat(durationType.fromDuration(duration)).isEqualTo(duration)
        assertThat(durationType.valueFromDB(duration.inWholeNanoseconds)).isEqualTo(duration)
        assertThat(durationType.valueFromDB(7)).isEqualTo(7.nanoseconds)
    }

    @Test
    fun queryParameterFactoriesRenderPreparedSqlAndKeepOriginalValues() {
        val date = LocalDate(2024, 3, 1)
        val time = LocalTime(8, 9, 10, 11)
        val dateTime = LocalDateTime(date, time)
        val instant = Instant.parse("2024-03-01T08:09:10.000000011Z")
        val offsetDateTime = OffsetDateTime.of(2024, 3, 1, 8, 9, 10, 11, ZoneOffset.UTC)
        val duration = 3.seconds + 11.nanoseconds

        assertPreparedParameter(dateParam(date), date)
        assertPreparedParameter(timeParam(time), time)
        assertPreparedParameter(dateTimeParam(dateTime), dateTime)
        assertPreparedParameter(timestampParam(instant), instant)
        assertPreparedParameter(timestampWithTimeZoneParam(offsetDateTime), offsetDateTime)
        assertPreparedParameter(durationParam(duration), duration)
    }

    @Test
    fun literalFactoriesUseKotlinDatetimeColumnTypesAndKeepOriginalValues() {
        val date = LocalDate(2024, 3, 1)
        val time = LocalTime(8, 9, 10, 11)
        val dateTime = LocalDateTime(date, time)
        val instant = Instant.parse("2024-03-01T08:09:10.000000011Z")
        val offsetDateTime = OffsetDateTime.of(2024, 3, 1, 8, 9, 10, 11, ZoneOffset.UTC)
        val duration = 3.seconds + 11.nanoseconds

        val dateLiteralExpression = dateLiteral(date)
        val timeLiteralExpression = timeLiteral(time)
        val dateTimeLiteralExpression = dateTimeLiteral(dateTime)
        val timestampLiteralExpression = timestampLiteral(instant)
        val offsetDateTimeLiteralExpression = timestampWithTimeZoneLiteral(offsetDateTime)
        val durationLiteralExpression = durationLiteral(duration)

        assertKotlinLocalDateType(dateLiteralExpression.columnType)
        assertKotlinLocalTimeType(timeLiteralExpression.columnType)
        assertKotlinLocalDateTimeType(dateTimeLiteralExpression.columnType)
        assertKotlinInstantType(timestampLiteralExpression.columnType)
        assertKotlinOffsetDateTimeType(offsetDateTimeLiteralExpression.columnType)
        assertKotlinDurationType(durationLiteralExpression.columnType)
        assertLiteralValue(dateLiteralExpression, date)
        assertLiteralValue(timeLiteralExpression, time)
        assertLiteralValue(dateTimeLiteralExpression, dateTime)
        assertLiteralValue(timestampLiteralExpression, instant)
        assertLiteralValue(offsetDateTimeLiteralExpression, offsetDateTime)
        assertLiteralValue(durationLiteralExpression, duration)
    }

    @Test
    fun customFunctionFactoriesUseKotlinDatetimeColumnTypes() {
        val date = LocalDate(2024, 4, 5)
        val time = LocalTime(6, 7, 8, 9)
        val dateTime = LocalDateTime(date, time)
        val instant = Instant.parse("2024-04-05T06:07:08.000000009Z")
        val offsetDateTime = OffsetDateTime.of(2024, 4, 5, 6, 7, 8, 9, ZoneOffset.ofHours(-3))
        val duration = 9.seconds

        val dateFunction = CustomDateFunction("DATE_IDENTITY", dateParam(date))
        val timeFunction = CustomTimeFunction("TIME_IDENTITY", timeParam(time))
        val dateTimeFunction = CustomDateTimeFunction("DATETIME_IDENTITY", dateTimeParam(dateTime))
        val timestampFunction = CustomTimeStampFunction("TIMESTAMP_IDENTITY", timestampParam(instant))
        val offsetDateTimeFunction = CustomTimestampWithTimeZoneFunction(
            "OFFSET_IDENTITY",
            timestampWithTimeZoneParam(offsetDateTime)
        )
        val durationFunction = CustomDurationFunction("DURATION_IDENTITY", durationParam(duration))

        assertKotlinLocalDateType(dateFunction.columnType)
        assertKotlinLocalTimeType(timeFunction.columnType)
        assertKotlinLocalDateTimeType(dateTimeFunction.columnType)
        assertKotlinInstantType(timestampFunction.columnType)
        assertKotlinOffsetDateTimeType(offsetDateTimeFunction.columnType)
        assertKotlinDurationType(durationFunction.columnType)
        assertThat(renderPrepared(dateFunction)).isEqualTo(PreparedSql("DATE_IDENTITY(?)", listOf(date)))
        assertThat(renderPrepared(timeFunction)).isEqualTo(PreparedSql("TIME_IDENTITY(?)", listOf(time)))
        assertThat(renderPrepared(dateTimeFunction)).isEqualTo(PreparedSql("DATETIME_IDENTITY(?)", listOf(dateTime)))
        assertThat(renderPrepared(timestampFunction)).isEqualTo(PreparedSql("TIMESTAMP_IDENTITY(?)", listOf(instant)))
        assertThat(renderPrepared(offsetDateTimeFunction)).isEqualTo(PreparedSql("OFFSET_IDENTITY(?)", listOf(offsetDateTime)))
        assertThat(renderPrepared(durationFunction)).isEqualTo(PreparedSql("DURATION_IDENTITY(?)", listOf(duration)))
    }

    @Test
    fun dateTimeExtractionFunctionsExposeExpectedResultTypes() {
        assertKotlinLocalDateType(Date(DateTimeTable.localDateTime).columnType)
        assertKotlinLocalDateType(Date(DateTimeTable.instant).columnType)
        assertKotlinLocalDateType(Date(DateTimeTable.offsetDateTime).columnType)
        assertKotlinLocalDateType(DateTimeTable.localDateTime.date().columnType)
        assertKotlinLocalDateType(DateTimeTable.instant.date().columnType)
        assertKotlinLocalDateType(DateTimeTable.offsetDateTime.date().columnType)

        assertKotlinLocalTimeType(Time(DateTimeTable.localDateTime).columnType)
        assertKotlinLocalTimeType(Time(DateTimeTable.instant).columnType)
        assertKotlinLocalTimeType(Time(DateTimeTable.offsetDateTime).columnType)
        assertKotlinLocalTimeType(DateTimeTable.localDateTime.time().columnType)
        assertKotlinLocalTimeType(DateTimeTable.instant.time().columnType)
        assertKotlinLocalTimeType(DateTimeTable.offsetDateTime.time().columnType)

        assertIntegerFunction(Month(DateTimeTable.localDate))
        assertIntegerFunction(DateTimeTable.localDate.year())
        assertIntegerFunction(DateTimeTable.localDate.month())
        assertIntegerFunction(DateTimeTable.localDate.day())
        assertIntegerFunction(DateTimeTable.localDateTime.hour())
        assertIntegerFunction(DateTimeTable.localDateTime.minute())
        assertIntegerFunction(DateTimeTable.localDateTime.second())
        assertIntegerFunction(DateTimeTable.instant.year())
        assertIntegerFunction(DateTimeTable.instant.month())
        assertIntegerFunction(DateTimeTable.instant.day())
        assertIntegerFunction(DateTimeTable.instant.hour())
        assertIntegerFunction(DateTimeTable.instant.minute())
        assertIntegerFunction(DateTimeTable.instant.second())
        assertIntegerFunction(DateTimeTable.offsetDateTime.year())
        assertIntegerFunction(DateTimeTable.offsetDateTime.month())
        assertIntegerFunction(DateTimeTable.offsetDateTime.day())
        assertIntegerFunction(DateTimeTable.offsetDateTime.hour())
        assertIntegerFunction(DateTimeTable.offsetDateTime.minute())
        assertIntegerFunction(DateTimeTable.offsetDateTime.second())
    }

    @Test
    fun currentDateTimeFunctionsUseKotlinDatetimeResultTypes() {
        assertKotlinLocalDateType(CurrentDate.columnType)
        assertKotlinLocalDateTimeType(CurrentDateTime.columnType)
        assertKotlinInstantType(CurrentTimestamp.columnType)
        assertKotlinOffsetDateTimeType(CurrentTimestampWithTimeZone.columnType)
    }

    private object DateTimeTable : Table("date_time_events") {
        val localDate = date("local_date")
        val localDateTime = datetime("local_date_time")
        val localTime = time("local_time")
        val instant = timestamp("instant_value")
        val offsetDateTime = timestampWithTimeZone("offset_date_time")
        val duration = duration("duration_value")
    }

    private data class PreparedSql(val sql: String, val arguments: List<Any?>)

    private fun renderPrepared(expression: Expression<*>): PreparedSql {
        val queryBuilder = QueryBuilder(true)
        expression.toQueryBuilder(queryBuilder)
        return PreparedSql(queryBuilder.toString(), queryBuilder.args.map { it.second })
    }

    private fun assertPreparedParameter(expression: Expression<*>, expectedValue: Any) {
        assertThat(renderPrepared(expression)).isEqualTo(PreparedSql("?", listOf(expectedValue)))
    }

    private fun assertLiteralValue(expression: LiteralOp<*>, expectedValue: Any) {
        assertThat(expression.value).isEqualTo(expectedValue)
    }

    private fun assertKotlinLocalDateType(columnType: Any): KotlinLocalDateColumnType {
        assertThat(columnType is KotlinLocalDateColumnType).isTrue()
        return columnType as KotlinLocalDateColumnType
    }

    private fun assertKotlinLocalDateTimeType(columnType: Any): KotlinLocalDateTimeColumnType {
        assertThat(columnType is KotlinLocalDateTimeColumnType).isTrue()
        return columnType as KotlinLocalDateTimeColumnType
    }

    private fun assertKotlinLocalTimeType(columnType: Any): KotlinLocalTimeColumnType {
        assertThat(columnType is KotlinLocalTimeColumnType).isTrue()
        return columnType as KotlinLocalTimeColumnType
    }

    private fun assertKotlinInstantType(columnType: Any): KotlinInstantColumnType {
        assertThat(columnType is KotlinInstantColumnType).isTrue()
        return columnType as KotlinInstantColumnType
    }

    private fun assertKotlinOffsetDateTimeType(columnType: Any): KotlinOffsetDateTimeColumnType {
        assertThat(columnType is KotlinOffsetDateTimeColumnType).isTrue()
        return columnType as KotlinOffsetDateTimeColumnType
    }

    private fun assertKotlinDurationType(columnType: Any): KotlinDurationColumnType {
        assertThat(columnType is KotlinDurationColumnType).isTrue()
        return columnType as KotlinDurationColumnType
    }

    private fun assertIntegerFunction(function: Function<Int>) {
        assertThat(function.columnType.valueFromDB(42)).isEqualTo(42)
    }
}
