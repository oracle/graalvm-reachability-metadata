/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.jr.ob.JSONObjectException;
import com.fasterxml.jackson.jr.ob.impl.SimpleValueReader;
import com.fasterxml.jackson.jr.ob.impl.ValueLocatorBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SimpleValueReaderDynamicAccessTest {
    @Test
    void resolvesClassNamesFromTheCurrentParserToken() throws Exception {
        SimpleValueReader reader = classReader();

        try (JsonParser parser = jsonParser(quotedClassName())) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);

            Class<?> resolved = (Class<?>) reader.read(null, parser);

            assertThat(resolved).isEqualTo(SimpleValueReaderDynamicAccessLateBoundType.class);
        }
    }

    @Test
    void resolvesClassNamesWhenAdvancingTheParser() throws Exception {
        SimpleValueReader reader = classReader();

        try (JsonParser parser = jsonParser(quotedClassName())) {
            Class<?> resolved = (Class<?>) reader.readNext(null, parser);

            assertThat(resolved).isEqualTo(SimpleValueReaderDynamicAccessLateBoundType.class);
        }
    }

    @Test
    void reportsInvalidClassNamesAfterAttemptingLookup() throws Exception {
        SimpleValueReader reader = classReader();

        try (JsonParser parser = jsonParser(quotedMissingClassName())) {
            assertThatThrownBy(() -> reader.readNext(null, parser))
                    .isInstanceOf(JSONObjectException.class)
                    .hasMessageContaining("Failed to bind `java.lang.Class`")
                    .hasMessageContaining(missingClassName());
        }
    }

    private static SimpleValueReader classReader() {
        return new SimpleValueReader(Class.class, ValueLocatorBase.SER_CLASS);
    }

    private static JsonParser jsonParser(String json) throws IOException {
        return new JsonFactory().createParser(json);
    }

    private static String quotedClassName() {
        return '"' + className() + '"';
    }

    private static String quotedMissingClassName() {
        return '"' + missingClassName() + '"';
    }

    private static String className() {
        Class<?> valueType = new SimpleValueReaderDynamicAccessLateBoundType().getClass();
        return valueType.getPackageName() + "." + valueType.getSimpleName();
    }

    private static String missingClassName() {
        return className() + "Missing";
    }
}

final class SimpleValueReaderDynamicAccessLateBoundType {
}
