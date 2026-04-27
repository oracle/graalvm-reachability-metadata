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
import com.fasterxml.jackson.jr.ob.impl.JSONReader;
import com.fasterxml.jackson.jr.ob.impl.POJODefinition;
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

    public static class EnumDefinitionExtension extends JacksonJrExtension {
        @Override
        protected void register(ExtensionContext ctxt) {
            ctxt.insertModifier(new EnumDefinitionModifier());
        }
    }

    public static class EnumDefinitionModifier extends ReaderWriterModifier {
        @Override
        public POJODefinition pojoDefinitionForDeserialization(JSONReader readContext, Class<?> pojoType) {
            if (pojoType != Direction.class) {
                return null;
            }
            return new POJODefinition(Direction.class, enumProps(), null, null, null);
        }

        private static POJODefinition.Prop[] enumProps() {
            return new POJODefinition.Prop[] {
                    enumProp("go-north", "NORTH"),
                    enumProp("go-south", "SOUTH")
            };
        }

        private static POJODefinition.Prop enumProp(String externalName, String enumConstantName) {
            return new POJODefinition.Prop(externalName, enumField(enumConstantName), null, null, null, null);
        }

        private static Field enumField(String enumConstantName) {
            try {
                return Direction.class.getField(enumConstantName);
            } catch (NoSuchFieldException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
