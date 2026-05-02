/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongo_java_driver;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class DateTimeFormatterInnerJaxbDateTimeFormatterTest {
    private static final String JAXB_FORMATTER_CLASS_NAME = "org.bson.json.DateTimeFormatter$JaxbDateTimeFormatter";
    private static final String DATE_TIME_STRING = "2019-01-02T03:04:05Z";

    @Test
    void parsesAndFormatsIsoDateTimesThroughJaxbFallback() throws ReflectiveOperationException {
        final Object formatter = newJaxbDateTimeFormatter();
        final long expectedEpochMillis = Instant.parse(DATE_TIME_STRING).toEpochMilli();

        final long parsedEpochMillis = parse(formatter, DATE_TIME_STRING);
        final String formattedDateTime = format(formatter, parsedEpochMillis);

        assertThat(parsedEpochMillis).isEqualTo(expectedEpochMillis);
        assertThat(parse(formatter, formattedDateTime)).isEqualTo(expectedEpochMillis);
    }

    private static Object newJaxbDateTimeFormatter() throws ReflectiveOperationException {
        final Constructor<?> constructor = Class.forName(JAXB_FORMATTER_CLASS_NAME).getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static long parse(final Object formatter, final String dateTimeString) throws ReflectiveOperationException {
        final Method parseMethod = formatter.getClass().getDeclaredMethod("parse", String.class);
        parseMethod.setAccessible(true);
        return (Long) parseMethod.invoke(formatter, dateTimeString);
    }

    private static String format(final Object formatter, final long epochMillis) throws ReflectiveOperationException {
        final Method formatMethod = formatter.getClass().getDeclaredMethod("format", long.class);
        formatMethod.setAccessible(true);
        return (String) formatMethod.invoke(formatter, epochMillis);
    }
}
