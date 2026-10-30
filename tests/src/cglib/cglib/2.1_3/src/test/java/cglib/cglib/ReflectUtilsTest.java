/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.sf.cglib.core.ReflectUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectUtilsTest {
    @Test
    void resolvesQualifiedConstructorAndCreatesValue() {
        Constructor<?> constructor = ReflectUtils.findConstructor(
                Message.class.getName() + "(java.lang.String)", Message.class.getClassLoader());

        Message message = (Message) ReflectUtils.newInstance(constructor, new Object[]{"hello"});

        assertThat(message.text()).isEqualTo("hello");
        assertThat(ReflectUtils.getSignature(constructor).getDescriptor())
                .isEqualTo("(Ljava/lang/String;)V");
    }

    @Test
    void resolvesShortNamesPrimitiveTypesAndArrays() {
        Constructor<?> constructor = ReflectUtils.findConstructor("String(char[], int, int)");
        String value = (String) ReflectUtils.newInstance(
                constructor, new Object[]{new char[]{'a', 'b', 'c', 'd'}, 1, 2});
        Method method = ReflectUtils.findMethod("String.valueOf(char[], int, int)");

        assertThat(value).isEqualTo("bc");
        assertThat(method.getParameterTypes()).containsExactly(char[].class, int.class, int.class);
        assertThat(ReflectUtils.getSignature(method).getDescriptor()).isEqualTo("([CII)Ljava/lang/String;");
    }

    @Test
    void resolvesObjectArrayParameters() {
        Method method = ReflectUtils.findMethod(
                ArrayConsumer.class.getName() + ".accept(String[][], int[])");

        assertThat(method.getDeclaringClass()).isEqualTo(ArrayConsumer.class);
        assertThat(method.getParameterTypes()).containsExactly(String[][].class, int[].class);
        assertThat(ReflectUtils.getSignature(method).getDescriptor())
                .isEqualTo("([[Ljava/lang/String;[I)I");
    }

    @Test
    void createsObjectsThroughNonPublicConstructors() {
        SecretMessage message = (SecretMessage) ReflectUtils.newInstance(
                SecretMessage.class, new Class<?>[]{String.class}, new Object[]{"private"});

        assertThat(message.text).isEqualTo("private");
    }

    @Test
    void findsInheritedMethodsAndSingleMethodContracts() throws NoSuchMethodException {
        Method inherited = ReflectUtils.findDeclaredMethod(Child.class, "text", new Class<?>[0]);
        Method contract = ReflectUtils.findInterfaceMethod(TextSource.class);
        Method factory = ReflectUtils.findNewInstance(MessageFactory.class);

        assertThat(inherited.getDeclaringClass()).isEqualTo(Message.class);
        assertThat(contract.getName()).isEqualTo("text");
        assertThat(contract.getReturnType()).isEqualTo(String.class);
        assertThat(factory.getName()).isEqualTo("newInstance");
        assertThat(factory.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void collectsClassSuperclassAndInterfaceMethods() {
        List<Method> methods = new ArrayList<>();

        assertThat(ReflectUtils.addAllMethods(Child.class, methods)).isSameAs(methods);
        assertThat(methods).extracting(Method::getName).contains("text", "length", "toString");
        assertThat(methods).extracting(Method::getDeclaringClass)
                .contains(Child.class, Message.class, TextSource.class, Object.class);
    }

    public interface TextSource {
        String text();
    }

    public interface MessageFactory {
        Message newInstance(String text);
    }

    public static class Message implements TextSource {
        private final String text;

        public Message(String text) {
            this.text = text;
        }

        @Override
        public String text() {
            return text;
        }
    }

    public static class Child extends Message {
        public Child() {
            super("child");
        }

        public int length() {
            return text().length();
        }
    }

    public static class ArrayConsumer {
        public int accept(String[][] rows, int[] weights) {
            return rows.length + weights.length;
        }
    }

    private static final class SecretMessage {
        private final String text;

        private SecretMessage(String text) {
            this.text = text;
        }
    }
}
