/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.StatefulBeanToCsvBuilder;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StatefulBeanToCsvBuilderTest {
    private static final String IGNORE_FIELD_INCONSISTENT_MESSAGE = "When specifying a field to ignore";

    @Test
    void rejectsInconsistentIgnoredFieldWithConfiguredLocale() throws NoSuchFieldException {
        Field otherField = OtherBean.class.getDeclaredField("other");

        assertThatThrownBy(() -> new StatefulBeanToCsvBuilder<SimpleBean>(new StringWriter())
                .withErrorLocale(Locale.US)
                .withIgnoreField(SimpleBean.class, otherField))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(IGNORE_FIELD_INCONSISTENT_MESSAGE);
    }

    public static class SimpleBean {
        public String name;
    }

    public static class OtherBean {
        public String other;
    }
}
