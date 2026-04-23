/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.util.LinkedHashMap;

import com.fasterxml.jackson.jr.ob.JSON;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleValueReaderDynamicAccessTest {
    @Test
    void resolvesTopLevelClassesFromStringValues() throws Exception {
        Class<?> resolved = JSON.std.beanFrom(Class.class, '"' + LinkedHashMap.class.getName() + '"');

        assertThat(resolved).isSameAs(LinkedHashMap.class);
    }

    @Test
    void resolvesClassTypedBeanPropertiesFromStringValues() throws Exception {
        TypeHolder holder = JSON.std.beanFrom(TypeHolder.class,
                "{\"type\":\"" + SampleType.class.getName() + "\"}");

        assertThat(holder.type).isSameAs(SampleType.class);
    }

    public static final class TypeHolder {
        public Class<?> type;
    }

    public static final class SampleType {
    }
}
