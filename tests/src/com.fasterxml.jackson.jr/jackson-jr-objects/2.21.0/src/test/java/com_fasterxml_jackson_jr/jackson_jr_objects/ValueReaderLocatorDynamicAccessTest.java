/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.JacksonJrExtension;
import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.api.ExtensionContext;
import com.fasterxml.jackson.jr.ob.api.ReaderWriterModifier;
import com.fasterxml.jackson.jr.ob.api.ValueReader;
import com.fasterxml.jackson.jr.ob.impl.BeanConstructors;
import com.fasterxml.jackson.jr.ob.impl.JSONReader;
import com.fasterxml.jackson.jr.ob.impl.POJODefinition;
import com.fasterxml.jackson.jr.ob.impl.ValueReaderLocator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

public class ValueReaderLocatorDynamicAccessTest {
    @Test
    void readsEnumsFromModifierProvidedDefinitions() throws Exception {
        Direction direction = enumAwareJson().beanFrom(Direction.class, "\"go-north\"");

        assertThat(direction).isEqualTo(Direction.NORTH);
    }

    @Test
    void readsModifierProvidedEnumDefinitionsCaseInsensitively() throws Exception {
        Direction direction = enumAwareJson(JSON.Feature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .beanFrom(Direction.class, "\"GO-SOUTH\"");

        assertThat(direction).isEqualTo(Direction.SOUTH);
    }

    @Test
    void readsLibraryEnumValuesFromModifierProvidedDefinitions() throws Exception {
        JSON.Feature feature = enumAwareJson().beanFrom(JSON.Feature.class, "\"field-access\"");

        assertThat(feature).isEqualTo(JSON.Feature.USE_FIELDS);
    }

    @Test
    void createsEnumReadersFromModifierProvidedDefinitions() {
        ValueReaderLocator locator = ValueReaderLocator.blueprint(null, new EnumDefinitionModifier());

        ValueReader reader = locator.findReader(Direction.class);

        assertThat(reader).isNotNull();
    }

    @Test
    void readsModifierNamedEnumValuesAsBeanProperties() throws Exception {
        TravelPlan plan = enumAwareJson(JSON.Feature.USE_FIELDS)
                .beanFrom(TravelPlan.class, "{\"direction\":\"go-north\"}");

        assertThat(plan.direction).isEqualTo(Direction.NORTH);
    }

    @Test
    void readsRecordPropertiesThroughBeanReader() throws Exception {
        TripSummary summary = JSON.std.beanFrom(TripSummary.class,
                "{\"destination\":\"Reykjavik\",\"travelers\":2}");

        assertThat(summary.destination()).isEqualTo("Reykjavik");
        assertThat(summary.travelers()).isEqualTo(2);
    }

    private static JSON enumAwareJson(JSON.Feature... features) {
        return JSON.builder()
                .enable(features)
                .register(new EnumDefinitionExtension())
                .build();
    }

    public enum Direction {
        NORTH,
        SOUTH
    }

    public static class TravelPlan {
        public Direction direction;
    }

    public record TripSummary(String destination, int travelers) {
    }

    public static class EnumDefinitionExtension extends JacksonJrExtension {
        @Override
        protected void register(ExtensionContext ctxt) {
            ctxt.insertModifier(new EnumDefinitionModifier());
        }
    }

    public static class EnumDefinitionModifier extends ReaderWriterModifier {
        @Override
        public POJODefinition pojoDefinitionForDeserialization(JSONReader readContext, Class<?> pojoType) {
            if (pojoType == Direction.class) {
                return new POJODefinition(Direction.class, directionProps(), new BeanConstructors(Direction.class));
            }
            if (pojoType == JSON.Feature.class) {
                return new POJODefinition(JSON.Feature.class, featureProps(), new BeanConstructors(JSON.Feature.class));
            }
            return null;
        }

        private static POJODefinition.Prop[] directionProps() {
            return new POJODefinition.Prop[] {
                    enumProp(Direction.class, "go-north", "NORTH"),
                    enumProp(Direction.class, "go-south", "SOUTH")
            };
        }

        private static POJODefinition.Prop[] featureProps() {
            return new POJODefinition.Prop[] {
                    enumProp(JSON.Feature.class, "field-access", "USE_FIELDS")
            };
        }

        private static POJODefinition.Prop enumProp(Class<?> enumType, String externalName, String enumConstantName) {
            return new POJODefinition.Prop(externalName, externalName,
                    enumField(enumType, enumConstantName), null, null, null, null);
        }

        private static Field enumField(Class<?> enumType, String enumConstantName) {
            try {
                return enumType.getField(enumConstantName);
            } catch (NoSuchFieldException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
