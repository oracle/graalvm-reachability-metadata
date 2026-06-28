/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.internal.cglib.core.ReflectUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ReflectUtilsTest {
    @Test
    void findsConstructorFromDescriptorWithPrimitiveArrayParameter() throws Exception {
        Constructor<?> constructor = ReflectUtils.findConstructor("java.lang.String(char[])");

        assertThat(constructor.getDeclaringClass()).isEqualTo(String.class);
        assertThat(constructor.getParameterTypes()).containsExactly(char[].class);

        Object instance = ReflectUtils.newInstance(constructor, new Object[] {new char[] {'s', 'i', 's', 'u'}});
        assertThat(instance).isEqualTo("sisu");
    }

    @Test
    void findsMethodFromDescriptorUsingDefaultPackageImports() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        Method method = ReflectUtils.findMethod("String.substring(int, int)", classLoader);

        assertThat(method.getDeclaringClass()).isEqualTo(String.class);
        assertThat(method.getName()).isEqualTo("substring");
        assertThat(method.getParameterTypes()).containsExactly(int.class, int.class);
    }

    @Test
    void constructsObjectWithDeclaredConstructor() {
        Object fixture = ReflectUtils.newInstance(
                ReflectUtilsConstructorFixture.class,
                new Class<?>[] {String.class},
                new Object[] {"created"});

        assertThat(fixture).isInstanceOf(ReflectUtilsConstructorFixture.class);
        assertThat(((ReflectUtilsConstructorFixture) fixture).value()).isEqualTo("created");
    }

    @Test
    void findsDeclaredMethodOnSuperclass() throws Exception {
        Method method = ReflectUtils.findDeclaredMethod(
                ReflectUtilsDeclaredMethodChild.class,
                "message",
                new Class<?>[] {String.class});

        assertThat(method.getDeclaringClass()).isEqualTo(ReflectUtilsDeclaredMethodParent.class);
        assertThat(method.getName()).isEqualTo("message");
    }

    @Test
    void collectsDeclaredMethodsAcrossClassHierarchyAndInterfaces() {
        List<Method> methods = new ArrayList<Method>();

        ReflectUtils.addAllMethods(ReflectUtilsMethodCollectorChild.class, methods);

        assertThat(methodNames(methods)).contains("childValue", "parentValue", "interfaceValue");
    }

    @Test
    void findsSingleInterfaceMethod() {
        Method method = ReflectUtils.findInterfaceMethod(ReflectUtilsSingleMethodFactory.class);

        assertThat(method.getDeclaringClass()).isEqualTo(ReflectUtilsSingleMethodFactory.class);
        assertThat(method.getName()).isEqualTo("newInstance");
    }

    private static List<String> methodNames(List<Method> methods) {
        List<String> names = new ArrayList<String>();
        for (Method method : methods) {
            names.add(method.getName());
        }
        return names;
    }
}

class ReflectUtilsConstructorFixture {
    private final String value;

    private ReflectUtilsConstructorFixture(String value) {
        this.value = value;
    }

    String value() {
        return value;
    }
}

class ReflectUtilsDeclaredMethodParent {
    String message(String input) {
        return "parent:" + input;
    }
}

class ReflectUtilsDeclaredMethodChild extends ReflectUtilsDeclaredMethodParent {
}

interface ReflectUtilsMethodCollectorInterface {
    String interfaceValue();
}

class ReflectUtilsMethodCollectorParent {
    String parentValue() {
        return "parent";
    }
}

class ReflectUtilsMethodCollectorChild extends ReflectUtilsMethodCollectorParent
        implements ReflectUtilsMethodCollectorInterface {
    String childValue() {
        return "child";
    }

    @Override
    public String interfaceValue() {
        return "interface";
    }
}

interface ReflectUtilsSingleMethodFactory {
    Object newInstance();
}
