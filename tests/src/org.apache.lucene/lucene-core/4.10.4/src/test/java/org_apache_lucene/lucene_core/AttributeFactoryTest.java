/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttributeImpl;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttributeImpl;
import org.apache.lucene.util.Attribute;
import org.apache.lucene.util.AttributeFactory;
import org.apache.lucene.util.AttributeImpl;
import org.junit.jupiter.api.Test;

public class AttributeFactoryTest {
    @Test
    public void createsConfiguredStaticImplementationWithPublicAttributeFactoryApi() {
        RecordingAttributeFactory delegate = new RecordingAttributeFactory();
        AttributeFactory factory = AttributeFactory.getStaticImplementation(delegate, TypeAttributeImpl.class);

        AttributeImpl implementation = factory.createAttributeInstance(TypeAttribute.class);

        assertThat(implementation).isInstanceOf(TypeAttributeImpl.class);
        TypeAttribute typeAttribute = (TypeAttribute) implementation;
        typeAttribute.setType("integration-token");
        assertThat(typeAttribute.type()).isEqualTo("integration-token");
    }

    @Test
    public void delegatesWhenStaticImplementationDoesNotImplementRequestedAttribute() {
        RecordingAttributeFactory delegate = new RecordingAttributeFactory();
        AttributeFactory factory = AttributeFactory.getStaticImplementation(delegate, TypeAttributeImpl.class);

        AttributeImpl fallbackImplementation = factory.createAttributeInstance(KeywordAttribute.class);

        assertThat(fallbackImplementation).isInstanceOf(KeywordAttributeImpl.class);
        assertThat(delegate.getRequestedAttributeClass()).isEqualTo(KeywordAttribute.class);
    }

    private static final class RecordingAttributeFactory extends AttributeFactory {
        private Class<? extends Attribute> requestedAttributeClass;

        @Override
        public AttributeImpl createAttributeInstance(Class<? extends Attribute> attClass) {
            requestedAttributeClass = attClass;
            return new KeywordAttributeImpl();
        }

        Class<? extends Attribute> getRequestedAttributeClass() {
            return requestedAttributeClass;
        }
    }
}
