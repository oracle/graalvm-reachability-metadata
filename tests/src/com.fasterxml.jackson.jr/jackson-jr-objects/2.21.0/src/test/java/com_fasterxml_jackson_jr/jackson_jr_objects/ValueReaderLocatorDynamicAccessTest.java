/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.lang.reflect.Field;
import java.util.List;

import com.fasterxml.jackson.jr.ob.JacksonJrExtension;
import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.api.CollectionBuilder;
import com.fasterxml.jackson.jr.ob.api.ExtensionContext;
import com.fasterxml.jackson.jr.ob.api.MapBuilder;
import com.fasterxml.jackson.jr.ob.api.ReaderWriterModifier;
import com.fasterxml.jackson.jr.ob.api.ValueReader;
import com.fasterxml.jackson.jr.ob.impl.BeanConstructors;
import com.fasterxml.jackson.jr.ob.impl.BeanPropertyIntrospector;
import com.fasterxml.jackson.jr.ob.impl.JSONReader;
import com.fasterxml.jackson.jr.ob.impl.POJODefinition;
import com.fasterxml.jackson.jr.ob.impl.ValueReaderLocator;
import org.junit.jupiter.api.Test;

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
    void createsEnumReadersFromModifierProvidedDefinitions() {
        ValueReaderLocator locator = ValueReaderLocator.blueprint(null, new EnumDefinitionModifier());

        ValueReader reader = locator.findReader(Direction.class);

        assertThat(reader).isNotNull();
    }

    @Test
    void createsEnumReadersFromDefinitionFields() {
        ExposedValueReaderLocator locator = new ExposedValueReaderLocator(new EnumDefinitionModifier());

        ValueReader reader = locator.enumReaderFor(Direction.class);

        assertThat(reader.valueType()).isEqualTo(Direction.class);
    }

    @Test
    void deserializesRecordPropertiesFromDefinitionOriginalNames() throws Exception {
        JSON json = JSON.builder()
                .register(new RecordDefinitionExtension())
                .build();

        RecordWithExternalNames item = json.beanFrom(RecordWithExternalNames.class,
                "{\"item_id\":\"A-1\",\"stock_count\":3}");

        assertThat(item).isEqualTo(new RecordWithExternalNames("A-1", 3));
    }

    @Test
    void readsLibraryEnumsFromModifierProvidedDefinitions() throws Exception {
        JSON.Feature feature = enumAwareJson().beanFrom(JSON.Feature.class, "\"case-insensitive-enums\"");

        assertThat(feature).isEqualTo(JSON.Feature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }

    @Test
    void deserializesRecordPropertiesThroughValueReaderLocator() throws Exception {
        ValueReaderLocator blueprint = ValueReaderLocator.blueprint(null, null);
        JSONReader readContext = new JSONReader(CollectionBuilder.defaultImpl(), MapBuilder.defaultImpl());
        ValueReader reader = blueprint.perOperationInstance(readContext, 0).findReader(InventoryItem.class);
        InventoryItem item = JSON.std.beanFrom(InventoryItem.class,
                "{\"id\":\"A-1\",\"quantity\":3,\"available\":true}");

        assertThat(reader).isNotNull();
        assertThat(item).isEqualTo(new InventoryItem("A-1", 3, true));
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

    public record InventoryItem(String id, int quantity, boolean available) {
    }

    public record RecordWithExternalNames(String id, int quantity) {
    }

    private static POJODefinition.Prop recordProp(String externalName, String originalName) {
        return new POJODefinition.Prop(externalName, originalName, null, null, null, null, null);
    }

    public static class ExposedValueReaderLocator extends ValueReaderLocator {
        ExposedValueReaderLocator(ReaderWriterModifier modifier) {
            super(null, modifier);
        }

        ValueReader enumReaderFor(Class<?> enumType) {
            return enumReader(enumType);
        }
    }

    public static class EnumDefinitionExtension extends JacksonJrExtension {
        @Override
        protected void register(ExtensionContext ctxt) {
            ctxt.insertModifier(new EnumDefinitionModifier());
        }
    }

    public static class RecordDefinitionExtension extends JacksonJrExtension {
        @Override
        protected void register(ExtensionContext ctxt) {
            ctxt.insertModifier(new RecordDefinitionModifier());
        }
    }

    public static class RecordDefinitionModifier extends ReaderWriterModifier {
        @Override
        public POJODefinition pojoDefinitionForDeserialization(JSONReader readContext, Class<?> pojoType) {
            if (pojoType != RecordWithExternalNames.class) {
                return null;
            }
            POJODefinition base = BeanPropertyIntrospector.instance()
                    .pojoDefinitionForDeserialization(readContext, pojoType);
            return base.withProperties(List.of(
                    recordProp("item_id", "id"),
                    recordProp("stock_count", "quantity")));
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
                    enumProp(JSON.Feature.class, "case-insensitive-enums", "ACCEPT_CASE_INSENSITIVE_ENUMS"),
                    enumProp(JSON.Feature.class, "force-reflection-access", "FORCE_REFLECTION_ACCESS")
            };
        }

        private static POJODefinition.Prop enumProp(Class<?> enumType, String externalName, String enumConstantName) {
            return new POJODefinition.Prop(externalName, enumConstantName, enumField(enumType, enumConstantName),
                    null, null, null, null);
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
