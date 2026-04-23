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
        Direction direction = enumAwareJson().beanFrom(Direction.class, "\"north\"");

        assertThat(direction).isEqualTo(Direction.NORTH);
    }

    private static JSON enumAwareJson() {
        return JSON.builder().register(new EnumDefinitionExtension()).build();
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
            return new POJODefinition(Direction.class, new POJODefinition.Prop[]{
                    prop("north", "NORTH"),
                    prop("south", "SOUTH")
            }, null, null, null);
        }

        private static POJODefinition.Prop prop(String jsonName, String enumConstant) {
            return new POJODefinition.Prop(jsonName, enumField(enumConstant), null, null, null, null);
        }

        private static Field enumField(String enumConstant) {
            try {
                return Direction.class.getField(enumConstant);
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
