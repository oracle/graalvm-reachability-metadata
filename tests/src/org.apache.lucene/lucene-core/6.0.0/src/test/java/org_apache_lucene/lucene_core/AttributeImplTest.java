/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.lucene.util.Attribute;
import org.apache.lucene.util.AttributeImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AttributeImplTest {
    @Test
    void defaultReflectWithReflectsDeclaredInstanceFields() {
        SingleAttributeImpl attribute = new SingleAttributeImpl();
        attribute.setValues("sample", 12);

        Map<String, Object> reflectedValues = new LinkedHashMap<>();
        attribute.reflectWith((attributeClass, key, value) -> {
            assertThat(attributeClass).isEqualTo(SingleAttribute.class);
            reflectedValues.put(key, value);
        });

        assertThat(reflectedValues)
                .containsEntry("term", "sample")
                .containsEntry("position", 12)
                .doesNotContainKey("STATIC_VALUE");
    }

    private interface SingleAttribute extends Attribute {
        String term();

        int position();
    }

    private static final class SingleAttributeImpl extends AttributeImpl implements SingleAttribute {
        private static final String STATIC_VALUE = "ignored";

        private String term;
        private int position;

        void setValues(String newTerm, int newPosition) {
            term = newTerm;
            position = newPosition;
        }

        @Override
        public String term() {
            return term;
        }

        @Override
        public int position() {
            return position;
        }

        @Override
        public void clear() {
            term = null;
            position = 0;
        }

        @Override
        public void copyTo(AttributeImpl target) {
            SingleAttributeImpl other = (SingleAttributeImpl) target;
            other.setValues(term, position);
        }
    }
}
