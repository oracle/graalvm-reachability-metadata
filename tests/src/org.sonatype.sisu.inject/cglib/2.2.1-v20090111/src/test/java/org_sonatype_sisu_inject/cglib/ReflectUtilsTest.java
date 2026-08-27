/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu_inject.cglib;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.sf.cglib.core.ReflectUtils;
import org.junit.jupiter.api.Test;

public class ReflectUtilsTest {

    @Test
    void resolvesConstructorsAndCreatesInstances() {
        Constructor<?> constructor = ReflectUtils.findConstructor(
                ConstructorTarget.class.getName() + "(java.lang.String)");

        assertThat(constructor.getParameterTypes()).containsExactly(String.class);

        ConstructorTarget target = (ConstructorTarget) ReflectUtils.newInstance(
                ConstructorTarget.class, new Class<?>[]{String.class}, new Object[]{"cglib"});

        assertThat(target.value).isEqualTo("cglib");
    }

    @Test
    void resolvesMethodsWithPrimitiveArrayParameters() {
        Method method = ReflectUtils.findMethod(MethodTarget.class.getName() + ".accept(int[])");

        assertThat(method.getDeclaringClass()).isEqualTo(MethodTarget.class);
        assertThat(method.getName()).isEqualTo("accept");
    }

    @Test
    void findsInheritedMethodsAndCollectsClassMethods() throws NoSuchMethodException {
        Method inherited = ReflectUtils.findDeclaredMethod(ChildTarget.class, "inherited", new Class<?>[0]);
        List<Method> methods = ReflectUtils.addAllMethods(ChildTarget.class, new ArrayList<Method>());

        assertThat(inherited.getDeclaringClass()).isEqualTo(ParentTarget.class);
        assertThat(methods).extracting(Method::getName).contains("child", "inherited");
    }

    @Test
    void findsSingleInterfaceMethod() {
        Method method = ReflectUtils.findInterfaceMethod(SingleMethodContract.class);

        assertThat(method.getName()).isEqualTo("create");
    }
}

class ConstructorTarget {
    final String value;

    public ConstructorTarget(String value) {
        this.value = value;
    }
}

class MethodTarget {
    public void accept(int[] values) {
    }
}

class ParentTarget {
    public void inherited() {
    }
}

class ChildTarget extends ParentTarget {
    public void child() {
    }
}

interface SingleMethodContract {
    Object create();
}
