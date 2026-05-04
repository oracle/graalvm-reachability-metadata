/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import org.apache.lucene.analysis.tokenattributes.FlagsAttribute;
import org.apache.lucene.analysis.tokenattributes.FlagsAttributeImpl;
import org.apache.lucene.util.AttributeFactory;
import org.apache.lucene.util.AttributeImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AttributeSupportTest {
    @Test
    void reflectsFieldsFromAttributeImplementation() {
        FlagsAttributeImpl attribute = new FlagsAttributeImpl();
        attribute.setFlags(7);

        assertThat(attribute.reflectAsString(false)).contains("flags=7");
    }

    @Test
    void createsAttributeImplementationFromDefaultFactory() {
        AttributeFactory factory = AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY;
        AttributeImpl attribute = factory.createAttributeInstance(FlagsAttribute.class);

        assertThat(attribute).isInstanceOf(FlagsAttributeImpl.class);
        assertThat(((FlagsAttribute) attribute).getFlags()).isZero();
    }
}
