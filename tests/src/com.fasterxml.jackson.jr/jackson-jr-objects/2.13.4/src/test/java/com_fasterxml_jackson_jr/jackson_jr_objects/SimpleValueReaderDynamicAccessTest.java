/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.jr.ob.JSON;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleValueReaderDynamicAccessTest {
    @Test
    void resolvesTopLevelClassesFromStringValues() throws Exception {
        String className = runtimeJdkClassName();

        Class<?> resolved = JSON.std.beanFrom(Class.class, '"' + className + '"');

        assertThat(resolved.getName()).isEqualTo(className);
    }

    @Test
    void resolvesClassTypedBeanPropertiesFromStringValues() throws Exception {
        String className = runtimeJdkClassName();

        TypeHolder holder = JSON.std.beanFrom(TypeHolder.class,
                "{\"type\":\"" + className + "\"}");

        assertThat(holder.type.getName()).isEqualTo(className);
    }

    @Test
    void resolvesClassesInsideTypedMapsAndLists() throws Exception {
        String className = runtimeJdkClassName();

        Map<String, Class> classesByName = JSON.std.mapOfFrom(Class.class,
                "{\"primary\":\"" + className + "\"}");
        List<Class> classes = JSON.std.listOfFrom(Class.class,
                "[\"" + className + "\"]");

        assertThat(classesByName.get("primary").getName()).isEqualTo(className);
        assertThat(classes).singleElement().satisfies(type -> assertThat(type.getName()).isEqualTo(className));
    }

    private static String runtimeJdkClassName() {
        return System.getProperty(TypeHolder.class.getName(), String.join(".", "java", "util", "LinkedHashMap"));
    }

    public static final class TypeHolder {
        public Class<?> type;
    }
}
