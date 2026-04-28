/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.annotations.Copy;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.helpers.AnnotationHelper;
import com.univocity.parsers.annotations.helpers.AnnotationRegistry;
import com.univocity.parsers.annotations.helpers.MethodFilter;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationHelperTest {
    @Test
    void createsInstancesWithStringArrayAndDefaultConstructors() {
        ArgumentsFactory argumentsFactory = AnnotationHelper.newInstance(
                FactoryBase.class,
                ArgumentsFactory.class,
                new String[] {"alpha", "beta"});
        DefaultFactory defaultFactory = AnnotationHelper.newInstance(
                FactoryBase.class,
                DefaultFactory.class,
                new String[0]);

        assertThat(argumentsFactory.values()).containsExactly("alpha", "beta");
        assertThat(defaultFactory.values()).containsExactly("default");
    }

    @Test
    void appliesFormatterSettingsAndNestedDecimalSymbols() {
        FormatterBean formatter = new FormatterBean();

        AnnotationHelper.applyFormatSettings(formatter, new String[] {
                "groupingUsed=false",
                "decimalSeparator=,",
                "groupingSeparator=."});

        assertThat(formatter.isGroupingUsed()).isFalse();
        assertThat(formatter.getDecimalFormatSymbols().getDecimalSeparator()).isEqualTo(',');
        assertThat(formatter.getDecimalFormatSymbols().getGroupingSeparator()).isEqualTo('.');
    }

    @Test
    void discoversAnnotatedFieldsMethodsAndCopiedParsedAnnotationValues() {
        List<Field> fields = AnnotationHelper.getAnnotatedFields(CopiedParsedBean.class, CsvColumn.class);
        Parsed parsed = AnnotationHelper.findAnnotation(fields.get(0), Parsed.class);
        String[] copiedFieldNames = AnnotationRegistry.getValue(fields.get(0), parsed, "field", parsed.field());
        List<Method> methods = AnnotationHelper.getAnnotatedMethods(
                MethodAnnotatedBean.class,
                MethodFilter.ONLY_SETTERS,
                Parsed.class);

        assertThat(fields).extracting(Field::getName).containsExactly("sku");
        assertThat(copiedFieldNames).containsExactly("sku");
        assertThat(methods).extracting(Method::getName).containsExactly("setQuantity");
    }

    @Test
    void collectsDeclaredFieldsAcrossClassHierarchy() {
        Map<Field, ?> fields = AnnotationHelper.getAllFields(ChildBean.class);

        assertThat(fields.keySet()).extracting(Field::getName).contains("parentCode", "childCode");
    }

    public interface FactoryBase {
        List<String> values();
    }

    public static class ArgumentsFactory implements FactoryBase {
        private final List<String> values;

        public ArgumentsFactory(String[] values) {
            this.values = Arrays.asList(values.clone());
        }

        @Override
        public List<String> values() {
            return values;
        }
    }

    public static class DefaultFactory implements FactoryBase {
        public DefaultFactory() {
        }

        @Override
        public List<String> values() {
            return Arrays.asList("default");
        }
    }

    public static class FormatterBean {
        private boolean groupingUsed = true;
        private DecimalFormatSymbols decimalFormatSymbols = new DecimalFormatSymbols();

        public boolean isGroupingUsed() {
            return groupingUsed;
        }

        public void setGroupingUsed(boolean groupingUsed) {
            this.groupingUsed = groupingUsed;
        }

        public DecimalFormatSymbols getDecimalFormatSymbols() {
            return decimalFormatSymbols;
        }

        public void setDecimalFormatSymbols(DecimalFormatSymbols decimalFormatSymbols) {
            this.decimalFormatSymbols = decimalFormatSymbols;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Parsed
    public @interface CsvColumn {
        @Copy(to = Parsed.class, property = "field")
        String value();
    }

    public static class CopiedParsedBean {
        @CsvColumn("sku")
        private String sku;
    }

    public static class MethodAnnotatedBean {
        private int quantity;

        @Parsed(field = "quantity")
        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    public static class ParentBean {
        private String parentCode;
    }

    public static class ChildBean extends ParentBean {
        private String childCode;
    }
}
