/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.attributes.AttributeBuilderTestAccess;
import org.glassfish.grizzly.attributes.DefaultAttributeBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AttributeBuilderInitializerTest {
    private static final String DEFAULT_ATTRIBUTE_BUILDER_PROPERTY =
            "org.glassfish.grizzly.DEFAULT_ATTRIBUTE_BUILDER";

    @Test
    void defaultAttributeBuilderUsesConfiguredPublicBuilderClass() {
        String previousBuilderClass = System.getProperty(DEFAULT_ATTRIBUTE_BUILDER_PROPERTY);
        System.setProperty(DEFAULT_ATTRIBUTE_BUILDER_PROPERTY, ConfiguredAttributeBuilder.class.getName());

        try {
            AttributeBuilder builder = AttributeBuilderTestAccess.initBuilder();
            Attribute<String> attribute = builder.createAttribute("configured-builder-attribute", "configured-value");

            assertThat(builder).isExactlyInstanceOf(ConfiguredAttributeBuilder.class);
            assertThat(attribute.toString()).contains("configured-builder-attribute");
        } finally {
            restoreDefaultAttributeBuilderProperty(previousBuilderClass);
        }
    }

    private static void restoreDefaultAttributeBuilderProperty(String previousBuilderClass) {
        if (previousBuilderClass == null) {
            System.clearProperty(DEFAULT_ATTRIBUTE_BUILDER_PROPERTY);
        } else {
            System.setProperty(DEFAULT_ATTRIBUTE_BUILDER_PROPERTY, previousBuilderClass);
        }
    }

    public static class ConfiguredAttributeBuilder extends DefaultAttributeBuilder {
        public ConfiguredAttributeBuilder() {
        }
    }
}
