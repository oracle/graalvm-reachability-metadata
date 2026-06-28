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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectUtilTest {
    @Test
    void discoversFieldsConstructorsAndMethodsThenInvokesMethod() {
        MutableBean bean = ReflectUtil.newInstance(MutableBean.class);

        Constructor<MutableBean>[] constructors = ReflectUtil.getConstructors(MutableBean.class);
        assertThat(constructors).isNotEmpty();

        ReflectUtil.setFieldValue(bean, "number", "42");
        ReflectUtil.setFieldValue(bean, "name", "hutool");
        assertThat(ReflectUtil.getFieldValue(bean, "number")).isEqualTo(42);
        assertThat(ReflectUtil.getFieldValue(bean, "name")).isEqualTo("hutool");

        assertThat(fieldNames(ReflectUtil.getFields(MutableBean.class)))
                .contains("number", "name");

        Method describe = ReflectUtil.getPublicMethod(MutableBean.class, "describe", String.class);
        assertThat(describe).isNotNull();
        assertThat(methodNames(ReflectUtil.getPublicMethods(MutableBean.class)))
                .contains("describe");
        assertThat(methodNames(ReflectUtil.getMethods(MutableBean.class)))
                .contains("describe", "greeting");

        String description = ReflectUtil.invoke(bean, describe, "value");
        assertThat(description).isEqualTo("value:hutool:42");
    }

    @Test
    void instantiatesByClassNameArgumentsArrayTypeAndFallbackConstructor() {
        DefaultBean defaultBean = ReflectUtil.newInstance(DefaultBean.class.getName());
        assertThat(defaultBean.marker()).isEqualTo("default");

        ConstructedBean constructedBean = ReflectUtil.newInstance(ConstructedBean.class, "alpha", 7);
        assertThat(constructedBean.summary()).isEqualTo("alpha:7");

        int[] emptyInts = ReflectUtil.newInstanceIfPossible(int[].class);
        assertThat(emptyInts).isEmpty();

        OnlyParameterizedBean fallbackBean = ReflectUtil.newInstanceIfPossible(OnlyParameterizedBean.class);
        assertThat(fallbackBean).isNotNull();
        assertThat(fallbackBean.summary()).isEqualTo("0:<null>");
    }

    @Test
    void readsInterfaceMethodsUsingDirectLookupModes() {
        Method[] inheritedInterfaceMethods = ReflectUtil.getMethodsDirectly(GreetingContract.class, true, true);
        assertThat(methodNames(inheritedInterfaceMethods))
                .contains("greeting", "rename");

        Method[] declaredInterfaceMethods = ReflectUtil.getMethodsDirectly(GreetingContract.class, false, true);
        assertThat(methodNames(declaredInterfaceMethods))
                .contains("currentName", "greeting", "rename");
    }

    private static List<String> fieldNames(Field[] fields) {
        List<String> names = new ArrayList<>();
        for (Field field : fields) {
            names.add(field.getName());
        }
        return names;
    }

    private static List<String> methodNames(Method[] methods) {
        List<String> names = new ArrayList<>();
        for (Method method : methods) {
            names.add(method.getName());
        }
        return names;
    }

    public interface GreetingContract {
        default String greeting() {
            return "hello " + currentName();
        }

        void rename(String value);

        String currentName();
    }

    public static class MutableBean implements GreetingContract {
        private int number;
        private String name;

        public MutableBean() {
            this.name = "initial";
        }

        public String describe(String prefix) {
            return prefix + ":" + name + ":" + number;
        }

        @Override
        public void rename(String value) {
            this.name = value;
        }

        @Override
        public String currentName() {
            return name;
        }
    }

    public static class DefaultBean {
        public DefaultBean() {
        }

        public String marker() {
            return "default";
        }
    }

    public static class ConstructedBean {
        private final String name;
        private final Integer count;

        public ConstructedBean(String name, Integer count) {
            this.name = name;
            this.count = count;
        }

        public String summary() {
            return name + ":" + count;
        }
    }

    public static class OnlyParameterizedBean {
        private final int count;
        private final List<String> values;

        public OnlyParameterizedBean(int count, List<String> values) {
            this.count = count;
            this.values = values;
        }

        public String summary() {
            String value = values == null ? "<null>" : values.toString();
            return count + ":" + value;
        }
    }
}
