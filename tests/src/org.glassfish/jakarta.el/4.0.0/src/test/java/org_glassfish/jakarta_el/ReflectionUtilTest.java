/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.jakarta_el;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import com.sun.el.ExpressionFactoryImpl;
import com.sun.el.util.ReflectionUtil;

import jakarta.el.ELProcessor;
import jakarta.el.ExpressionFactory;
import jakarta.el.MethodExpression;
import jakarta.el.MethodInfo;
import jakarta.el.StandardELContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilTest {
    private final ExpressionFactory factory = new ExpressionFactoryImpl();

    @Test
    void invokesMethodWithExactParameterTypesAndReportsItsSignature() {
        StandardELContext context = contextFor(new MessageService());
        MethodExpression expression = factory.createMethodExpression(
                context, "#{service.greet}", String.class, new Class<?>[] {String.class});

        MethodInfo info = expression.getMethodInfo(context);

        assertThat(info.getName()).isEqualTo("greet");
        assertThat(info.getReturnType()).isEqualTo(String.class);
        assertThat(info.getParamTypes()).containsExactly(String.class);
        assertThat(expression.invoke(context, new Object[] {"Ada"})).isEqualTo("Welcome, Ada");
    }

    @Test
    void coercesArgumentsToTheMethodParameterTypes() {
        StandardELContext context = contextFor(new MessageService());
        MethodExpression expression = factory.createMethodExpression(
                context, "#{service.multiply}", Integer.class, new Class<?>[] {String.class, String.class});

        assertThat(expression.invoke(context, new Object[] {"6", "7"})).isEqualTo(42);
    }

    @Test
    void choosesTheMostSpecificAssignableOverload() {
        StandardELContext context = contextFor(new MessageService());
        MethodExpression expression = factory.createMethodExpression(
                context, "#{service.describe}", String.class, new Class<?>[] {String.class});

        assertThat(expression.invoke(context, new Object[] {"letter"})).isEqualTo("text:letter");
    }

    @Test
    void invokesMethodThroughPublicInterfaceOfNonPublicImplementation() {
        StandardELContext context = contextFor(new HiddenGreetingService());
        MethodExpression expression = factory.createMethodExpression(
                context, "#{service.greet}", String.class, new Class<?>[] {String.class});

        assertThat(expression.invoke(context, new Object[] {"Lin"})).isEqualTo("Hello, Lin");
    }

    @Test
    void invokesMethodThroughPublicSuperclassOfNonPublicSubclass() {
        StandardELContext context = contextFor(new HiddenMessageService());
        MethodExpression expression = factory.createMethodExpression(
                context, "#{service.greet}", String.class, new Class<?>[] {String.class});

        assertThat(expression.invoke(context, new Object[] {"Sam"})).isEqualTo("Welcome, Sam");
    }

    @Test
    void packsAndConvertsVariableArguments() {
        StandardELContext context = contextFor(new MessageService());
        MethodExpression expression = factory.createMethodExpression(
                context, "#{service.total}", Integer.class,
                new Class<?>[] {String.class, String.class, String.class});

        assertThat(expression.invoke(context, new Object[] {"4", "5", "6"})).isEqualTo(15);
    }

    @Test
    void invokesVariableArgumentMethodWithNoValues() {
        StandardELContext context = contextFor(new MessageService());
        MethodExpression expression = factory.createMethodExpression(
                context, "#{service.total}", Integer.class, new Class<?>[0]);

        assertThat(expression.invoke(context, new Object[0])).isEqualTo(0);
    }

    @Test
    void passesAnExistingVariableArgumentArray() {
        StandardELContext context = contextFor(new MessageService());
        MethodExpression expression = factory.createMethodExpression(
                context, "#{service.join}", String.class, new Class<?>[] {String[].class});

        assertThat(expression.invoke(context, new Object[] {new String[] {"red", "blue"}}))
                .isEqualTo("red/blue");
    }

    @Test
    void invokesMethodUsingConfiguredArrayParameterType() throws ClassNotFoundException {
        StandardELContext context = contextFor(new MessageService());
        Class<?> returnType = ReflectionUtil.forName("java.lang.String");
        Class<?>[] parameterTypes = ReflectionUtil.toTypeArray(new String[] {"java.lang.String[]"});
        MethodExpression expression = factory.createMethodExpression(
                context, "#{service.join}", returnType, parameterTypes);

        assertThat(expression.getMethodInfo(context).getParamTypes()).containsExactly(String[].class);
        assertThat(expression.invoke(context, new Object[] {new String[] {"configured", "signature"}}))
                .isEqualTo("configured/signature");
    }

    @Test
    void restoresPersistedMethodExpressionAndItsParameterTypes() throws Exception {
        ELProcessor processor = new ELProcessor();
        processor.defineBean("service", new MessageService());
        StandardELContext context = processor.getELManager().getELContext();
        MethodExpression original = factory.createMethodExpression(
                context, "#{service.join}", String.class, new Class<?>[] {String[].class});
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        MethodExpression restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (MethodExpression) input.readObject();
        }

        assertThat(restored.getExpressionString()).isEqualTo("#{service.join}");
        assertThat(restored.getMethodInfo(context).getParamTypes()).containsExactly(String[].class);
        assertThat(restored.invoke(context, new Object[] {new String[] {"saved", "expression"}}))
                .isEqualTo("saved/expression");
    }

    private StandardELContext contextFor(Object service) {
        StandardELContext context = new StandardELContext(factory);
        context.getVariableMapper().setVariable("service", factory.createValueExpression(service, Object.class));
        return context;
    }

    public interface GreetingService {
        String greet(String name);
    }

    static class HiddenGreetingService implements GreetingService {
        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    public static class MessageService {
        public String greet(String name) {
            return "Welcome, " + name;
        }

        public int multiply(int left, int right) {
            return left * right;
        }

        public String describe(Object value) {
            return "object:" + value;
        }

        public String describe(CharSequence value) {
            return "text:" + value;
        }

        public int total(int... values) {
            int result = 0;
            for (int value : values) {
                result += value;
            }
            return result;
        }

        public String join(String... values) {
            return String.join("/", values);
        }
    }

    static class HiddenMessageService extends MessageService {
    }
}
