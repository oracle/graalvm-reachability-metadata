/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.JSONObjectException;
import com.fasterxml.jackson.jr.ob.impl.SimpleValueReader;
import org.junit.jupiter.api.Test;

import static com.fasterxml.jackson.jr.ob.impl.ValueLocatorBase.SER_CLASS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void readsClassNamesWithSimpleValueReader() throws Exception {
        String className = loadableClassName();
        SimpleValueReader reader = new SimpleValueReader(Class.class, SER_CLASS);
        JsonFactory jsonFactory = new JsonFactory();

        try (JsonParser parser = jsonFactory.createParser('"' + className + '"')) {
            parser.nextToken();
            Object resolved = reader.read(null, parser);

            assertThat(resolved).isEqualTo(LoadableType.class);
        }
    }

    @Test
    void reportsUnresolvableClassNames() {
        String className = "missing." + getClass().getSimpleName() + System.nanoTime();

        assertThatThrownBy(() -> JSON.std.beanFrom(Class.class, '"' + className + '"'))
                .isInstanceOf(JSONObjectException.class)
                .hasMessage("Failed to bind `java.lang.Class` from value '" + className + "'");
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
