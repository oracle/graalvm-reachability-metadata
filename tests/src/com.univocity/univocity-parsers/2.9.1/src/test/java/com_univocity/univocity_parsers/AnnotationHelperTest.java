/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.annotations.Copy;
import com.univocity.parsers.annotations.HeaderTransformer;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.helpers.AnnotationHelper;
import com.univocity.parsers.annotations.helpers.AnnotationRegistry;
import com.univocity.parsers.annotations.helpers.MethodFilter;
import com.univocity.parsers.common.beans.PropertyWrapper;
import com.univocity.parsers.conversions.Validator;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotationHelperTest {
    @Test
    public void createsConfiguredAndDefaultInstances() {
        final PrefixHeaderTransformer transformer = AnnotationHelper.newInstance(
                HeaderTransformer.class,
                PrefixHeaderTransformer.class,
                new String[] {"customer-"}
        );
        assertEquals("customer-name", transformer.transformName((Field) null, "name"));

        final PassingValidator validator = AnnotationHelper.newInstance(
                Validator.class,
                PassingValidator.class,
                new String[0]
        );
        assertNull(validator.validate("accepted"));
    }

    @Test
    public void appliesFormatterSettingsThroughBeanProperties() {
        final DecimalFormat formatter = new DecimalFormat("###0.###");

        AnnotationHelper.applyFormatSettings(formatter, new String[] {
                "maximumIntegerDigits=3",
                "decimalSeparator=,"
        });

        assertEquals(3, formatter.getMaximumIntegerDigits());
        assertEquals(',', formatter.getDecimalFormatSymbols().getDecimalSeparator());
    }

    @Test
    public void discoversInheritedFieldsAndAnnotatedMethods() {
        final Map<Field, PropertyWrapper> fields = AnnotationHelper.getAllFields(ChildRecord.class);
        assertTrue(fields.keySet().stream().anyMatch(field -> "parentId".equals(field.getName())));
        assertTrue(fields.keySet().stream().anyMatch(field -> "name".equals(field.getName())));

        final List<Method> getters = AnnotationHelper.getAnnotatedMethods(ChildRecord.class, MethodFilter.ONLY_GETTERS);
        assertEquals(1, getters.size());
        assertEquals("getCategory", getters.get(0).getName());
    }

    @Test
    public void copiesCustomAnnotationValuesIntoParsedAnnotation() {
        AnnotationRegistry.reset();
        final List<Field> fields = AnnotationHelper.getAnnotatedFields(ChildRecord.class, ParsedColumn.class);
        assertFalse(fields.isEmpty());
        final Field nameField = fields.stream()
                .filter(field -> "name".equals(field.getName()))
                .findFirst()
                .orElseThrow(AssertionError::new);

        final Parsed parsed = AnnotationHelper.findAnnotation(nameField, Parsed.class);

        assertNotNull(parsed);
        assertArrayEquals(
                new String[] {"customer_name"},
                AnnotationRegistry.getValue(nameField, parsed, "field", parsed.field())
        );
        assertEquals(2, AnnotationRegistry.getValue(nameField, parsed, "index", parsed.index()));
    }

    @Parsed
    @Retention(RetentionPolicy.RUNTIME)
    @Target({FIELD, METHOD})
    public @interface ParsedColumn {
        @Copy(to = Parsed.class)
        String field() default "";

        @Copy(to = Parsed.class, property = "index")
        int position() default -1;
    }

    public static class PrefixHeaderTransformer extends HeaderTransformer {
        private final String prefix;

        public PrefixHeaderTransformer(String[] arguments) {
            prefix = arguments[0];
        }

        @Override
        public String transformName(Field field, String name) {
            return prefix + name;
        }
    }

    public static class PassingValidator implements Validator<Object> {
        @Override
        public String validate(Object value) {
            return null;
        }
    }

    public static class ParentRecord {
        private String parentId;

        public String getParentId() {
            return parentId;
        }

        public void setParentId(String parentId) {
            this.parentId = parentId;
        }
    }

    public static class ChildRecord extends ParentRecord {
        @ParsedColumn(field = "customer_name", position = 2)
        private String name;
        private String category;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @ParsedColumn(field = "category")
        public String getCategory() {
            return category;
        }

        @ParsedColumn(field = "category")
        public void setCategory(String category) {
            this.category = category;
        }
    }
}
