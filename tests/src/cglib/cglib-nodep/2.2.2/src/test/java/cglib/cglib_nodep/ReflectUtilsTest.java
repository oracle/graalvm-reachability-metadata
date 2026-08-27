/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.sf.cglib.core.ReflectUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectUtilsTest {
    @Test
    void resolvesConstructorsAndMethodsFromSignatures() {
        Constructor<?> constructor = ReflectUtils.findConstructor("java.lang.String(byte[])");
        Method method = ReflectUtils.findMethod("String.valueOf(int)");

        assertThat(constructor.getParameterTypes()).containsExactly(byte[].class);
        assertThat(method.getName()).isEqualTo("valueOf");
        assertThat(method.getParameterTypes()).containsExactly(int.class);
    }

    @Test
    void createsInstancesUsingDeclaredConstructors() {
        PrivateConstructor instance = (PrivateConstructor) ReflectUtils.newInstance(
                PrivateConstructor.class,
                new Class<?>[]{String.class},
                new Object[]{"cglib"}
        );

        assertThat(instance.value).isEqualTo("cglib");
    }

    @Test
    void findsDeclaredAndInterfaceMethods() throws NoSuchMethodException {
        Method declaredMethod = ReflectUtils.findDeclaredMethod(
                Child.class,
                "message",
                new Class<?>[]{String.class}
        );
        Method interfaceMethod = ReflectUtils.findInterfaceMethod(SingleMethodContract.class);

        assertThat(declaredMethod.getDeclaringClass()).isEqualTo(Parent.class);
        assertThat(interfaceMethod.getName()).isEqualTo("execute");
    }

    @Test
    void collectsMethodsFromClassHierarchy() {
        List<Method> methods = new ArrayList<>();
        ReflectUtils.addAllMethods(Child.class, methods);

        assertThat(methods)
                .extracting(Method::getName)
                .contains("message", "childMethod", "execute");
    }

    private static final class PrivateConstructor {
        private final String value;

        private PrivateConstructor(String value) {
            this.value = value;
        }
    }

    private static class Parent {
        private String message(String value) {
            return value;
        }
    }

    private static final class Child extends Parent implements SingleMethodContract {
        private void childMethod() {
        }

        @Override
        public void execute() {
        }
    }

    private interface SingleMethodContract {
        void execute();
    }
}
