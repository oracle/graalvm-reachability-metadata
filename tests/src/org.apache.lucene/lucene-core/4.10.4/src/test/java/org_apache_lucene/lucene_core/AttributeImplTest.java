/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttributeImpl;
import org.junit.jupiter.api.Test;

public class AttributeImplTest {
    @Test
    public void reflectsDeclaredFieldValuesForSingleAttributeImplementations() {
        TypeAttributeImpl attribute = new TypeAttributeImpl();
        attribute.setType("custom-token-type");
        List<String> reflectedValues = new ArrayList<>();

        attribute.reflectWith((attributeClass, key, value) -> reflectedValues.add(
                attributeClass.getName() + "#" + key + "=" + value));

        assertThat(reflectedValues).containsExactly(TypeAttribute.class.getName() + "#type=custom-token-type");
        assertThat(attribute.reflectAsString(false)).isEqualTo("type=custom-token-type");
        assertThat(attribute.reflectAsString(true))
                .isEqualTo(TypeAttribute.class.getName() + "#type=custom-token-type");
    }
}
