/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_web.javax_el;

import java.beans.FeatureDescriptor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.Map;
import java.util.ResourceBundle;

import javax.el.ArrayELResolver;
import javax.el.BeanELResolver;
import javax.el.CompositeELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.FunctionMapper;
import javax.el.ListELResolver;
import javax.el.MapELResolver;
import javax.el.MethodExpression;
import javax.el.MethodInfo;
import javax.el.PropertyNotWritableException;
import javax.el.ResourceBundleELResolver;
import javax.el.ValueExpression;
import javax.el.ValueReference;
import javax.el.VariableMapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Javax_elTest {

    @Test
    void expressionFactoryEvaluatesArithmeticCompositeFunctionsAndVariables() throws NoSuchMethodException {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        SimpleELContext context = newContext();
        context.setFunction("text", "fullName", FunctionLibrary.class.getMethod(
                "fullName", String.class, String.class));
        context.setVariable("person", factory.createValueExpression(new Person("Ada", "Lovelace", 36), Person.class));

        ValueExpression expression = factory.createValueExpression(
                context,
                "Hello ${text:fullName(person.firstName, person.lastName)}: ${person.age + 6}",
                String.class);

        assertThat(expression.getValue(context)).isEqualTo("Hello Ada Lovelace: 42");
        assertThat(expression.getExpectedType()).isEqualTo(String.class);
        assertThat(expression.getExpressionString()).contains("person.age + 6");
        assertThat(expression.isLiteralText()).isFalse();
    }

    @Test
    void valueExpressionsReadWriteNestedCollectionsAndExposeValueReferences() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        SimpleELContext context = newContext();
        List<String> items = new ArrayList<>();
        items.add("pen");
        items.add("pencil");
        Map<String, Object> cart = new HashMap<>();
        cart.put("items", items);
        context.setVariable("cart", factory.createValueExpression(cart, Map.class));

        ValueExpression itemExpression = factory.createValueExpression(context, "${cart.items[1]}", Object.class);

        assertThat(itemExpression.getValue(context)).isEqualTo("pencil");
        assertThat(itemExpression.getType(context)).isEqualTo(Object.class);

        ValueReference reference = itemExpression.getValueReference(context);
        assertThat(reference.getBase()).isSameAs(items);
        assertThat(reference.getProperty()).hasToString("1");

        itemExpression.setValue(context, "notebook");

        assertThat(items).containsExactly("pen", "notebook");
        assertThat(itemExpression.getValue(context)).isEqualTo("notebook");
    }

    @Test
    void methodExpressionsInvokeBeanMethodsAndReportMethodInformation() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        SimpleELContext context = newContext();
        context.setVariable("formatter", factory.createValueExpression(new Formatter(), Formatter.class));

        MethodExpression typedExpression = factory.createMethodExpression(
                context,
                "#{formatter.repeat}",
                String.class,
                new Class<?>[] {String.class, Long.class});

        MethodInfo methodInfo = typedExpression.getMethodInfo(context);
        assertThat(methodInfo.getName()).isEqualTo("repeat");
        assertThat(methodInfo.getReturnType()).isEqualTo(String.class);
        assertThat(methodInfo.getParamTypes()).containsExactly(String.class, Long.class);
        assertThat(typedExpression.isParmetersProvided()).isFalse();
        assertThat(typedExpression.invoke(context, new Object[] {"ha", 3L})).isEqualTo("hahaha");

        MethodExpression inlineExpression = factory.createMethodExpression(
                context,
                "#{formatter.repeat('ho', 2)}",
                String.class,
                new Class<?>[0]);
        assertThat(inlineExpression.isParmetersProvided()).isTrue();
        assertThat(inlineExpression.invoke(context, new Object[0])).isEqualTo("hoho");

        MethodExpression literalExpression = factory.createMethodExpression(
                context, "literal result", String.class, new Class<?>[0]);
        assertThat(literalExpression.invoke(context, new Object[0])).isEqualTo("literal result");
        assertThat(literalExpression.isLiteralText()).isTrue();
    }

    @Test
    void standardResolversHandleMapsListsArraysBundlesAndBeansDirectly() {
        SimpleELContext context = newContext();

        Map<String, String> map = new HashMap<>();
        map.put("language", "EL");
        MapELResolver mapResolver = new MapELResolver();
        assertThat(mapResolver.getValue(context, map, "language")).isEqualTo("EL");
        assertThat(context.isPropertyResolved()).isTrue();
        reset(context);
        mapResolver.setValue(context, map, "language", "Unified EL");
        assertThat(map.get("language")).isEqualTo("Unified EL");
        assertThat(mapResolver.getCommonPropertyType(context, map)).isEqualTo(Object.class);

        List<String> list = new ArrayList<>();
        list.add("zero");
        list.add("one");
        ListELResolver listResolver = new ListELResolver();
        reset(context);
        assertThat(listResolver.getValue(context, list, 1)).isEqualTo("one");
        reset(context);
        listResolver.setValue(context, list, 0, "ZERO");
        assertThat(list).containsExactly("ZERO", "one");

        String[] array = {"first", "second"};
        ArrayELResolver arrayResolver = new ArrayELResolver();
        reset(context);
        assertThat(arrayResolver.getType(context, array, 0)).isEqualTo(String.class);
        reset(context);
        arrayResolver.setValue(context, array, 1, "SECOND");
        assertThat(array).containsExactly("first", "SECOND");
        ArrayELResolver readOnlyArrayResolver = new ArrayELResolver(true);
        reset(context);
        assertThat(readOnlyArrayResolver.isReadOnly(context, array, 0)).isTrue();
        assertThatExceptionOfType(PropertyNotWritableException.class)
                .isThrownBy(() -> readOnlyArrayResolver.setValue(context, array, 0, "blocked"));

        ResourceBundle bundle = new TestBundle();
        ResourceBundleELResolver bundleResolver = new ResourceBundleELResolver();
        reset(context);
        assertThat(bundleResolver.getValue(context, bundle, "greeting")).isEqualTo("hello");
        reset(context);
        assertThat(bundleResolver.isReadOnly(context, bundle, "greeting")).isTrue();
        reset(context);
        assertThat(bundleResolver.getCommonPropertyType(context, bundle)).isEqualTo(String.class);

        BeanELResolver beanResolver = new BeanELResolver();
        Person person = new Person("Grace", "Hopper", 85);
        reset(context);
        assertThat(beanResolver.getValue(context, person, "firstName")).isEqualTo("Grace");
        reset(context);
        assertThat(beanResolver.getType(context, person, "age")).isEqualTo(int.class);
        reset(context);
        beanResolver.setValue(context, person, "firstName", "Amazing");
        assertThat(person.getFirstName()).isEqualTo("Amazing");
        reset(context);
        assertThat(beanResolver.invoke(
                context,
                new Formatter(),
                "join",
                new Class<?>[] {String.class, String.class},
                new Object[] {"left", "right"})).isEqualTo("left:right");

        Iterator<FeatureDescriptor> descriptors = beanResolver.getFeatureDescriptors(context, person);
        List<String> descriptorNames = new ArrayList<>();
        while (descriptors.hasNext()) {
            descriptorNames.add(descriptors.next().getName());
        }
        assertThat(descriptorNames).contains("firstName", "lastName", "age");
    }

    @Test
    void compositeResolverStopsAtFirstResolverThatResolvesProperty() {
        SimpleELContext context = newContext();
        CompositeELResolver resolver = new CompositeELResolver();
        resolver.add(new MapELResolver());
        resolver.add(new BeanELResolver());
        Map<String, Object> map = new HashMap<>();
        map.put("class", "map value wins");

        Object value = resolver.getValue(context, map, "class");

        assertThat(value).isEqualTo("map value wins");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void factoryCoercesValuesAndCreatesLiteralValueExpressions() {
        ExpressionFactory factory = ExpressionFactory.newInstance();

        assertThat(factory.coerceToType("42", Integer.class)).isEqualTo(42);
        assertThat(factory.coerceToType("true", Boolean.class)).isEqualTo(true);
        assertThat((BigDecimal) factory.coerceToType("19.75", BigDecimal.class)).isEqualByComparingTo("19.75");
        assertThat(factory.coerceToType(null, String.class)).isEqualTo("");

        ValueExpression literal = factory.createValueExpression("constant", String.class);

        assertThat(literal.getValue(newContext())).isEqualTo("constant");
        assertThat(literal.getExpectedType()).isEqualTo(String.class);
        assertThat(literal.isLiteralText()).isTrue();
        assertThat(literal.getExpressionString()).isEqualTo("constant");
        assertThatThrownBy(() -> literal.setValue(newContext(), "changed"))
                .isInstanceOf(PropertyNotWritableException.class);
    }

    private static SimpleELContext newContext() {
        CompositeELResolver resolver = new CompositeELResolver();
        resolver.add(new MapELResolver());
        resolver.add(new ListELResolver());
        resolver.add(new ArrayELResolver());
        resolver.add(new ResourceBundleELResolver());
        resolver.add(new BeanELResolver());
        return new SimpleELContext(resolver);
    }

    private static void reset(ELContext context) {
        context.setPropertyResolved(false);
    }

    public static final class SimpleELContext extends ELContext {
        private final ELResolver resolver;
        private final SimpleFunctionMapper functionMapper = new SimpleFunctionMapper();
        private final SimpleVariableMapper variableMapper = new SimpleVariableMapper();

        SimpleELContext(ELResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public ELResolver getELResolver() {
            return resolver;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return functionMapper;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return variableMapper;
        }

        void setFunction(String prefix, String localName, Method method) {
            functionMapper.setFunction(prefix, localName, method);
        }

        void setVariable(String name, ValueExpression expression) {
            variableMapper.setVariable(name, expression);
        }
    }

    public static final class SimpleFunctionMapper extends FunctionMapper {
        private final Map<String, Method> methods = new HashMap<>();

        @Override
        public Method resolveFunction(String prefix, String localName) {
            return methods.get(key(prefix, localName));
        }

        void setFunction(String prefix, String localName, Method method) {
            methods.put(key(prefix, localName), method);
        }

        private static String key(String prefix, String localName) {
            return prefix + ':' + localName;
        }
    }

    public static final class SimpleVariableMapper extends VariableMapper {
        private final Map<String, ValueExpression> expressions = new HashMap<>();

        @Override
        public ValueExpression resolveVariable(String variable) {
            return expressions.get(variable);
        }

        @Override
        public ValueExpression setVariable(String variable, ValueExpression expression) {
            return expressions.put(variable, expression);
        }
    }

    public static final class FunctionLibrary {
        private FunctionLibrary() {
        }

        public static String fullName(String firstName, String lastName) {
            return firstName + " " + lastName;
        }
    }

    public static final class Formatter {
        public String repeat(String value, Long count) {
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < count.intValue(); index++) {
                result.append(value);
            }
            return result.toString();
        }

        public String join(String left, String right) {
            return left + ':' + right;
        }
    }

    public static final class Person {
        private String firstName;
        private String lastName;
        private int age;

        public Person(String firstName, String lastName, int age) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.age = age;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    public static final class TestBundle extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                    {"greeting", "hello"},
                    {"farewell", "goodbye"}
            };
        }
    }
}
