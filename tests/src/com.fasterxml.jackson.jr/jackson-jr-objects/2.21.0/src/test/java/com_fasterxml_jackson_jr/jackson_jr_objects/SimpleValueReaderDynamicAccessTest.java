/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.JSONObjectException;
import com.fasterxml.jackson.jr.ob.ValueIterator;
import com.fasterxml.jackson.jr.ob.impl.SimpleValueReader;
import com.fasterxml.jackson.jr.ob.impl.ValueLocatorBase;
import org.junit.jupiter.api.Test;

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
    void resolvesClassesInsideTypedArrays() throws Exception {
        String json = "[\"" + String.class.getName() + "\",\""
                + Integer.class.getName() + "\"]";

        Class[] classes = JSON.std.arrayOfFrom(Class.class, json);

        assertThat(classes).containsExactly(String.class, Integer.class);
    }

    @Test
    void resolvesClassValuesFromBeanSequences() throws Exception {
        String json = "[\"" + String.class.getName() + "\",\""
                + Integer.class.getName() + "\"]";

        try (ValueIterator<Class> classes = JSON.std.beanSequenceFrom(Class.class, json)) {
            assertThat(classes.next()).isEqualTo(String.class);
            assertThat(classes.next()).isEqualTo(Integer.class);
            assertThat(classes.hasNext()).isFalse();
        }
    }

    @Test
    void directReaderResolvesRuntimeClassNamesFromParserValues() throws Exception {
        Object resolved = readClassValue('"' + runtimeClassName(String.class) + '"');

        assertThat(resolved).isEqualTo(String.class);
    }

    @Test
    void directReaderReportsClassLoadingFailuresFromParserValues() {
        assertThatThrownBy(() -> readClassValue('"' + missingClassName() + '"'))
                .isInstanceOf(JSONObjectException.class)
                .hasMessageContaining("Failed to bind `java.lang.Class`");
    }

    private static Object readClassValue(String json) throws IOException {
        try (JsonParser parser = new JsonFactory().createParser(json)) {
            parser.nextToken();
            return new SimpleValueReader(Class.class, ValueLocatorBase.SER_CLASS).read(null, parser);
        }
    }

    private static String runtimeClassName(Class<?> type) {
        String propertyName = "simple.value.reader.direct.class." + System.nanoTime();
        System.setProperty(propertyName, type.getName());
        return System.getProperty(propertyName);
    }

    private static String missingClassName() {
        return "missing.simple.value.reader.Type" + System.nanoTime();
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
