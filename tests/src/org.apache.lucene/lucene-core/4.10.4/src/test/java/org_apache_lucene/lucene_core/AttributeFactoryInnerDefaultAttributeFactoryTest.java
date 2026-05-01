/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.lucene.analysis.tokenattributes.FlagsAttribute;
import org.apache.lucene.analysis.tokenattributes.FlagsAttributeImpl;
import org.apache.lucene.util.AttributeFactory;
import org.apache.lucene.util.AttributeImpl;
import org.junit.jupiter.api.Test;

public class AttributeFactoryInnerDefaultAttributeFactoryTest {
    @Test
    public void createsImplementationByAttributeNamingConvention() {
        AttributeImpl implementation = AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY.createAttributeInstance(FlagsAttribute.class);

        assertThat(implementation).isInstanceOf(FlagsAttributeImpl.class);
        FlagsAttribute flagsAttribute = (FlagsAttribute) implementation;
        flagsAttribute.setFlags(123);
        assertThat(flagsAttribute.getFlags()).isEqualTo(123);
    }
}
