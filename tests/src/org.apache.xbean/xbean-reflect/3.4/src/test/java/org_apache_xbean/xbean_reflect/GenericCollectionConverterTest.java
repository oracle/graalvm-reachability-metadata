/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_reflect;

import java.util.ArrayList;

import org.apache.xbean.propertyeditor.GenericCollectionConverter;
import org.apache.xbean.propertyeditor.StringEditor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GenericCollectionConverterTest {
    @Test
    void createsRequestedCollectionImplementationFromDelimitedText() {
        GenericCollectionConverter converter = new GenericCollectionConverter(RecordingCollection.class, new StringEditor());

        Object value = converter.toObject("[alpha, beta, gamma]");

        assertThat(value).isInstanceOf(RecordingCollection.class);
        assertThat((RecordingCollection) value).containsExactly("alpha", "beta", "gamma");
    }

    public static class RecordingCollection extends ArrayList<String> {
        public RecordingCollection() {
            super();
        }
    }
}
