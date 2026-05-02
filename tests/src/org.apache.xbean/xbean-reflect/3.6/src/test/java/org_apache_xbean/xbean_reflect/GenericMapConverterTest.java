/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_reflect;

import java.util.LinkedHashMap;

import org.apache.xbean.propertyeditor.GenericMapConverter;
import org.apache.xbean.propertyeditor.StringEditor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GenericMapConverterTest {
    @Test
    void createsRequestedMapImplementationFromPropertiesText() {
        GenericMapConverter converter = new GenericMapConverter(RecordingMap.class, new StringEditor(), new StringEditor());

        Object value = converter.toObject("first=alpha\nsecond=beta\n");

        assertThat(value).isInstanceOf(RecordingMap.class);
        RecordingMap map = (RecordingMap) value;
        assertThat(map.wasCreatedByNoArgumentConstructor()).isTrue();
        assertThat(map)
                .containsEntry("first", "alpha")
                .containsEntry("second", "beta");
    }

    public static class RecordingMap extends LinkedHashMap<String, String> {
        private final boolean createdByNoArgumentConstructor;

        public RecordingMap() {
            super();
            createdByNoArgumentConstructor = true;
        }

        public boolean wasCreatedByNoArgumentConstructor() {
            return createdByNoArgumentConstructor;
        }
    }
}
