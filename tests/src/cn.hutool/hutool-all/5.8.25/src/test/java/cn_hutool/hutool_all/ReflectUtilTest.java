/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.util.ReflectUtil;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectUtilTest {
    @Test
    public void createsInstancesUsingClassNamesConstructorsAndFallbacks() {
        Object builder = ReflectUtil.newInstance("java.lang.StringBuilder");
        assertThat(builder).isInstanceOf(StringBuilder.class);

        DefaultConstructable defaultConstructable = ReflectUtil.newInstance(DefaultConstructable.class);
        assertThat(defaultConstructable.describe()).isEqualTo("default:1");

        ParameterizedConstructable parameterized = ReflectUtil.newInstance(
                ParameterizedConstructable.class, "named", 4);
        assertThat(parameterized.describe()).isEqualTo("named:4");

        OnlyParameterizedConstructable possible = ReflectUtil.newInstanceIfPossible(
                OnlyParameterizedConstructable.class);
        assertThat(possible).isNotNull();
        assertThat(possible.value).isZero();

        String[] emptyArray = ReflectUtil.newInstanceIfPossible(String[].class);
        assertThat(emptyArray).isEmpty();
    }

    @Test
    public void discoversConstructorsFieldsAndMethods() {
        Constructor<?>[] constructors = ReflectUtil.getConstructorsDirectly(ParameterizedConstructable.class);
        assertThat(constructors).anySatisfy(constructor -> assertThat(constructor.getParameterCount()).isEqualTo(2));

        Field[] fields = ReflectUtil.getFieldsDirectly(ReflectiveSubject.class, true);
        assertThat(fields).extracting(Field::getName).contains("message", "count", "inherited");

        Method[] classMethods = ReflectUtil.getMethodsDirectly(ReflectiveSubject.class, true, true);
        assertThat(classMethods).extracting(Method::getName).contains("combine", "defaultLabel", "baseMethod");

        Method[] declaredInterfaceMethods = ReflectUtil.getMethodsDirectly(ReflectiveContract.class, false, true);
        assertThat(declaredInterfaceMethods).extracting(Method::getName).contains("contractMethod", "defaultLabel");

        Method[] inheritedInterfaceMethods = ReflectUtil.getMethodsDirectly(ReflectiveContract.class, true, true);
        assertThat(inheritedInterfaceMethods).extracting(Method::getName).contains("contractMethod", "defaultLabel");

        Method[] publicMethods = ReflectUtil.getPublicMethods(ReflectiveSubject.class);
        assertThat(publicMethods).extracting(Method::getName).contains("combine");

        Method publicMethod = ReflectUtil.getPublicMethod(ReflectiveSubject.class, "combine", String.class, int.class);
        assertThat(publicMethod).isNotNull();
    }

    @Test
    public void readsWritesAndInvokesMembersThroughReflectUtil() {
        ReflectiveSubject subject = new ReflectiveSubject();

        assertThat(ReflectUtil.getFieldValue(subject, "message")).isEqualTo("initial");
        ReflectUtil.setFieldValue(subject, "message", "changed");
        ReflectUtil.setFieldValue(subject, "count", "12");

        assertThat(ReflectUtil.getFieldValue(subject, "message")).isEqualTo("changed");
        assertThat(ReflectUtil.getFieldValue(subject, "count")).isEqualTo(12);

        String combined = ReflectUtil.invoke(subject, "combine", "value", 5);
        assertThat(combined).isEqualTo("changed:value:17");
    }

    public static class DefaultConstructable {
        private final String name;
        private final int count;

        public DefaultConstructable() {
            this.name = "default";
            this.count = 1;
        }

        public String describe() {
            return name + ":" + count;
        }
    }

    public static class ParameterizedConstructable {
        private final String name;
        private final int count;

        public ParameterizedConstructable(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String describe() {
            return name + ":" + count;
        }
    }

    public static class OnlyParameterizedConstructable {
        private final int value;

        public OnlyParameterizedConstructable(int value) {
            this.value = value;
        }
    }

    public interface ReflectiveContract {
        String contractMethod();

        default String defaultLabel() {
            return "default-label";
        }
    }

    public static class ReflectiveBase {
        private String inherited = "base";

        public String baseMethod() {
            return inherited;
        }
    }

    public static class ReflectiveSubject extends ReflectiveBase implements ReflectiveContract {
        private String message = "initial";
        private int count = 3;

        @Override
        public String contractMethod() {
            return message;
        }

        public String combine(String suffix, int increment) {
            return message + ":" + suffix + ":" + (count + increment);
        }
    }
}
