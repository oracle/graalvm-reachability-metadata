/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencsv.bean.StatefulBeanToCsvBuilder;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class StatefulBeanToCsvBuilderTest {
    @Test
    void rejectsNullIgnoredField() {
        Writer writer = new StringWriter();
        StatefulBeanToCsvBuilder<Object> builder = new StatefulBeanToCsvBuilder<>(writer)
                .withErrorLocale(Locale.ROOT);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> builder.withIgnoreField(Object.class, null));

        String expectedMessage = "When specifying a field to ignore, both the type and the field "
                + "must be non-null, and the field must be a member of the type, either directly "
                + "or through inheritance.";
        assertThat(exception).hasMessage(expectedMessage);
    }
}
