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
import com.fasterxml.jackson.jr.ob.impl.SimpleValueReader;
import com.fasterxml.jackson.jr.ob.impl.ValueLocatorBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleValueReaderDynamicAccessTest {
    @Test
    void simpleReaderResolvesClassValuesDirectly() throws Exception {
        JsonFactory jsonFactory = new JsonFactory();
        SimpleValueReader reader = new SimpleValueReader(Class.class,
                ValueLocatorBase.SER_CLASS);

        try (JsonParser parser = jsonFactory.createParser("\"java.lang.String\"")) {
            parser.nextToken();

            Object resolved = reader.read(null, parser);

            assertThat(resolved).isEqualTo(String.class);
        }
    }

    @Test
    void resolvesTopLevelClassesFromStringValues() throws Exception {
        String className = loadableClassName();

        Class<?> resolved = JSON.std.beanFrom(Class.class, '"' + className + '"');

        assertThat(resolved.getName()).isEqualTo(className);
    }

    @Test
    void resolvesClassTypedBeanPropertiesFromStringValues() throws Exception {
        String className = loadableClassName();

        TypeHolder holder = JSON.std.beanFrom(TypeHolder.class,
                "{\"type\":\"" + className + "\"}");

        assertThat(holder.type.getName()).isEqualTo(className);
    }

    @Test
    void resolvesClassesInsideTypedMapsAndLists() throws Exception {
        String className = loadableClassName();

        Map<String, Class> classesByName = JSON.std.mapOfFrom(Class.class,
                "{\"primary\":\"" + className + "\"}");
        List<Class> classes = JSON.std.listOfFrom(Class.class,
                "[\"" + className + "\"]");

        assertThat(classesByName.get("primary").getName()).isEqualTo(className);
        assertThat(classes).singleElement().satisfies(resolved ->
                assertThat(resolved.getName()).isEqualTo(className));
    }

    private static String loadableClassName() {
        return SimpleValueReaderDynamicAccessTest.class.getName() + "$LoadableType";
    }

    public static final class TypeHolder {
        public Class<?> type;
    }

    public static final class LoadableType {
    }
}
