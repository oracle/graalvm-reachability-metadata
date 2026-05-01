/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_reflect;

import java.beans.ConstructorProperties;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.xbean.recipe.Option;
import org.apache.xbean.recipe.ReflectionUtil;
import org.apache.xbean.recipe.ReflectionUtil.ConstructorFactory;
import org.apache.xbean.recipe.ReflectionUtil.StaticFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilTest {
    @Test
    void findsDeclaredAndInheritedFieldsByName() {
        Field declaredField = ReflectionUtil.findField(FieldTarget.class, "number", 1, null);
        Field inheritedField = ReflectionUtil.findField(FieldTarget.class, "inheritedText", "value", null);

        assertThat(declaredField.getName()).isEqualTo("number");
        assertThat(inheritedField.getName()).isEqualTo("inheritedText");
    }

    @Test
    void findsAllDeclaredAndInheritedFieldsByType() {
        List<Field> fields = ReflectionUtil.findAllFieldsByType(
                FieldTarget.class,
                "value",
                EnumSet.of(Option.PRIVATE_PROPERTIES));

        assertThat(fields).extracting(Field::getName).contains("localText", "hiddenText", "inheritedText");
    }

    @Test
    void findsDeclaredAndInheritedSettersByName() {
        List<Method> publicSetters = ReflectionUtil.findAllSetters(FieldTarget.class, "localText", "value", null);
        List<Method> privateSetters = ReflectionUtil.findAllSetters(
                FieldTarget.class,
                "hiddenText",
                "value",
                EnumSet.of(Option.PRIVATE_PROPERTIES));

        assertThat(publicSetters).extracting(Method::getName).contains("setLocalText");
        assertThat(privateSetters).extracting(Method::getName).contains("setHiddenText");
    }

    @Test
    void findsDeclaredAndInheritedSettersByType() {
        List<Method> setters = ReflectionUtil.findAllSettersByType(FieldTarget.class, Integer.valueOf(7), null);

        assertThat(setters).extracting(Method::getName).contains("setNumber", "setInheritedNumber");
    }

    @Test
    void findsPublicAndPrivateConstructors() {
        ConstructorFactory publicConstructor = ReflectionUtil.findConstructor(
                ConstructedTarget.class,
                Collections.emptyList(),
                null);
        ConstructorFactory privateConstructor = ReflectionUtil.findConstructor(
                ConstructedTarget.class,
                Collections.singletonList("value"),
                Collections.<Class<?>>singletonList(String.class),
                Collections.<String>emptySet(),
                EnumSet.of(Option.PRIVATE_CONSTRUCTOR));

        ConstructedTarget publicInstance = (ConstructedTarget) publicConstructor.create();
        ConstructedTarget privateInstance = (ConstructedTarget) privateConstructor.create("private");

        assertThat(publicInstance.getValue()).isEqualTo("public");
        assertThat(privateInstance.getValue()).isEqualTo("private");
    }

    @Test
    void findsStaticFactoriesFromPublicAndDeclaredMethods() {
        StaticFactory publicFactory = ReflectionUtil.findStaticFactory(
                FactoryTarget.class,
                "create",
                Collections.emptyList(),
                null);
        StaticFactory privateFactory = ReflectionUtil.findStaticFactory(
                FactoryTarget.class,
                "hidden",
                Collections.singletonList("value"),
                Collections.<Class<?>>singletonList(String.class),
                Collections.<String>emptySet(),
                EnumSet.of(Option.PRIVATE_FACTORY));

        FactoryTarget publicInstance = (FactoryTarget) publicFactory.create();
        FactoryTarget privateInstance = (FactoryTarget) privateFactory.create("private");

        assertThat(publicInstance.getValue()).isEqualTo("public");
        assertThat(privateInstance.getValue()).isEqualTo("private");
    }

    @Test
    void findsInstanceFactoryFromPublicAndDeclaredMethods() {
        Method factory = ReflectionUtil.findInstanceFactory(FactoryTarget.class, "build", null);
        Method privateFactory = ReflectionUtil.findInstanceFactory(
                FactoryTarget.class,
                "hiddenBuild",
                EnumSet.of(Option.PRIVATE_FACTORY));

        assertThat(factory.getName()).isEqualTo("build");
        assertThat(privateFactory.getName()).isEqualTo("hiddenBuild");
    }

    @Test
    void resolvesConstructorParameterNamesFromConstructorProperties() {
        Set<String> availableProperties = new LinkedHashSet<String>(Arrays.asList("name", "amount"));
        ConstructorFactory constructor = ReflectionUtil.findConstructor(
                NamedConstructorTarget.class,
                null,
                null,
                availableProperties,
                EnumSet.of(Option.NAMED_PARAMETERS));

        Object instance = constructor.create("coffee", Integer.valueOf(2));

        NamedConstructorTarget namedInstance = (NamedConstructorTarget) instance;

        assertThat(constructor.getParameterNames()).containsExactly("name", "amount");
        assertThat(namedInstance.getName()).isEqualTo("coffee");
        assertThat(namedInstance.getAmount()).isEqualTo(2);
    }

    public static class FieldBase {
        public String inheritedText;

        public void setInheritedNumber(Integer inheritedNumber) {
        }
    }

    public static class FieldTarget extends FieldBase {
        public int number;
        public String localText;
        private String hiddenText;

        public void setLocalText(String localText) {
            this.localText = localText;
        }

        public void setNumber(Integer number) {
            this.number = number;
        }

        private void setHiddenText(String hiddenText) {
            this.hiddenText = hiddenText;
        }
    }

    public static class ConstructedTarget {
        private final String value;

        public ConstructedTarget() {
            this.value = "public";
        }

        private ConstructedTarget(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static class FactoryTarget {
        private final String value;

        private FactoryTarget(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static FactoryTarget create() {
            return new FactoryTarget("public");
        }

        private static FactoryTarget hidden(String value) {
            return new FactoryTarget(value);
        }

        public FactoryTarget build() {
            return new FactoryTarget("instance");
        }

        private FactoryTarget hiddenBuild() {
            return new FactoryTarget("hidden-instance");
        }
    }

    public static class NamedConstructorTarget {
        private final String name;
        private final int amount;

        @ConstructorProperties({"name", "amount"})
        public NamedConstructorTarget(String name, int amount) {
            this.name = name;
            this.amount = amount;
        }

        public String getName() {
            return name;
        }

        public int getAmount() {
            return amount;
        }
    }
}
