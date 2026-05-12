/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.msgpack.template.builder.beans.ExceptionListener;
import org.msgpack.template.builder.beans.XMLDecoder;

public class XMLDecoderInnerSAXHandlerTest {
    @Test
    void decodesClassBackedFieldsArraysAndOwnerMethods() {
        final RecordingExceptionListener listener = new RecordingExceptionListener();
        final Owner owner = new Owner("decoded");
        final XMLDecoder decoder = new XMLDecoder(inputStreamFor("""
                <java>
                  <object class="java.lang.Integer" method="valueOf">
                    <string>7</string>
                  </object>
                  <array class="java.lang.String" length="2"/>
                  <object class="org_msgpack.msgpack.XMLDecoderInnerSAXHandlerTest$PublicConstants" field="MESSAGE"/>
                  <object method="joinWithPrefix">
                    <string>value</string>
                  </object>
                  <object method="addOne">
                    <int>41</int>
                  </object>
                </java>
                """), owner, listener);
        try {
            assertThat(decoder.readObject()).isEqualTo(Integer.valueOf(7));
            assertThat((String[]) decoder.readObject()).containsExactly(null, null);
            assertThat(decoder.readObject()).isEqualTo("field-value");
            assertThat(decoder.readObject()).isEqualTo("decoded:value");
            assertThat(decoder.readObject()).isEqualTo(Integer.valueOf(42));
            assertThat(listener.exceptions).isEmpty();
        } finally {
            decoder.close();
        }
    }

    private static ByteArrayInputStream inputStreamFor(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    public static final class PublicConstants {
        public static final String MESSAGE = "field-value";

        private PublicConstants() {
        }
    }

    public static final class Owner {
        private final String prefix;

        public Owner(String prefix) {
            this.prefix = prefix;
        }

        public String joinWithPrefix(String value) {
            return this.prefix + ":" + value;
        }

        public Integer addOne(int value) {
            return Integer.valueOf(value + 1);
        }
    }

    private static final class RecordingExceptionListener implements ExceptionListener {
        private final List<Exception> exceptions = new ArrayList<Exception>();

        @Override
        public void exceptionThrown(Exception exception) {
            this.exceptions.add(exception);
        }
    }
}
