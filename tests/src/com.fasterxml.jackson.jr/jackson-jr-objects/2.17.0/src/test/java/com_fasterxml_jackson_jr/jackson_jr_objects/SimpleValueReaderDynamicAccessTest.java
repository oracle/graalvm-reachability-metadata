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
        String className = loadableClassName();

        Class<?> resolved = JSON.std.beanFrom(Class.class, '"' + className + '"');

        assertThat(resolved).isEqualTo(LoadableType.class);
    }

    @Test
    void resolvesClassTypedBeanPropertiesFromStringValues() throws Exception {
        String className = loadableClassName();

        TypeHolder holder = JSON.std.beanFrom(TypeHolder.class,
                "{\"type\":\"" + className + "\"}");

        assertThat(holder.type).isEqualTo(LoadableType.class);
    }

    @Test
    void resolvesClassesInsideTypedMapsAndLists() throws Exception {
        String className = loadableClassName();

        Map<String, Class> classesByName = JSON.std.mapOfFrom(Class.class,
                "{\"primary\":\"" + className + "\"}");
        List<Class> classes = JSON.std.listOfFrom(Class.class,
                "[\"" + className + "\"]");

        assertThat(classesByName.get("primary")).isEqualTo(LoadableType.class);
        assertThat(classes).singleElement().isEqualTo(LoadableType.class);
    }

    private static String loadableClassName() {
        String propertyName = "simple.value.reader.class." + System.nanoTime();
        System.setProperty(propertyName, LoadableType.class.getName());
        return System.getProperty(propertyName);
    }

    public static final class TypeHolder {
        public Class<?> type;
    }

    public static final class LoadableType {
    }
}
