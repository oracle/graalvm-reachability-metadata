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
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectUtilTest {

    @Test
    void discoversConstructorsFieldsAndMethods() {
        Constructor<?>[] constructors = ReflectUtil.getConstructorsDirectly(SampleBean.class);
        assertThat(Arrays.stream(constructors).map(Constructor::getParameterCount))
                .contains(0, 2);

        Field[] ownFields = ReflectUtil.getFieldsDirectly(SampleBean.class, false);
        assertThat(Arrays.stream(ownFields).map(Field::getName))
                .contains("secret", "count")
                .doesNotContain("inherited");

        Field[] fieldsWithSuperClass = ReflectUtil.getFieldsDirectly(SampleBean.class, true);
        assertThat(Arrays.stream(fieldsWithSuperClass).map(Field::getName))
                .contains("secret", "count", "inherited");

        Method[] classMethods = ReflectUtil.getMethodsDirectly(SampleBean.class, true, true);
        assertThat(Arrays.stream(classMethods).map(Method::getName))
                .contains("combine", "publicGreeting", "defaultGreeting");

        Method[] interfaceMethodsWithSupers = ReflectUtil.getMethodsDirectly(NamedContract.class, true, true);
        assertThat(Arrays.stream(interfaceMethodsWithSupers).map(Method::getName))
                .contains("abstractName", "defaultGreeting");

        Method[] interfaceDeclaredMethods = ReflectUtil.getMethodsDirectly(NamedContract.class, false, true);
        assertThat(Arrays.stream(interfaceDeclaredMethods).map(Method::getName))
                .contains("abstractName", "defaultGreeting");

        Set<String> publicMethodNames = ReflectUtil.getPublicMethodNames(SampleBean.class);
        assertThat(publicMethodNames).contains("publicGreeting", "abstractName");

        Method publicMethod = ReflectUtil.getPublicMethod(SampleBean.class, "publicGreeting", String.class);
        assertThat(publicMethod).isNotNull();
        assertThat(publicMethod.getName()).isEqualTo("publicGreeting");
    }

    @Test
    void readsWritesFieldsAndInvokesMethods() {
        SampleBean bean = new SampleBean();

        ReflectUtil.setFieldValue(bean, "secret", "changed");
        ReflectUtil.setFieldValue(bean, "count", "7");

        assertThat(ReflectUtil.getFieldValue(bean, "secret")).isEqualTo("changed");
        assertThat(ReflectUtil.getFieldValue(bean, "count")).isEqualTo(7);

        Method combine = ReflectUtil.getMethod(SampleBean.class, "combine", String.class, int.class);
        assertThat(combine).isNotNull();
        String combined = ReflectUtil.invoke(bean, combine, "item", 3);
        assertThat(combined).isEqualTo("item:changed:7:3");
    }

    @Test
    void createsInstancesThroughEveryReflectUtilPath() {
        PublicNoArgBean fromClassName = ReflectUtil.newInstance(PublicNoArgBean.class.getName());
        assertThat(fromClassName.value()).isEqualTo("created");

        SampleBean defaultBean = ReflectUtil.newInstance(SampleBean.class);
        assertThat(defaultBean.publicGreeting("Hi")).isEqualTo("Hi default");

        SampleBean constructorBean = ReflectUtil.newInstance(SampleBean.class, "Ada", Integer.valueOf(41));
        assertThat(constructorBean.publicGreeting("Hello")).isEqualTo("Hello Ada");
        assertThat(ReflectUtil.getFieldValue(constructorBean, "count")).isEqualTo(41);

        String[] emptyArray = ReflectUtil.newInstanceIfPossible(String[].class);
        assertThat(emptyArray).isEmpty();

        RequiredArgumentsBean requiredArgumentsBean = ReflectUtil.newInstanceIfPossible(RequiredArgumentsBean.class);
        assertThat(requiredArgumentsBean.name()).isNull();
        assertThat(requiredArgumentsBean.count()).isZero();
    }

    public interface NamedContract {
        String abstractName();

        default String defaultGreeting() {
            return "default-greeting";
        }
    }

    public static class BaseBean {
        private String inherited = "base";
    }

    public static class SampleBean extends BaseBean implements NamedContract {
        private String secret;
        private int count;

        public SampleBean() {
            this("default", 0);
        }

        public SampleBean(String secret, Integer count) {
            this.secret = secret;
            this.count = count;
        }

        @Override
        public String abstractName() {
            return secret;
        }

        public String publicGreeting(String prefix) {
            return prefix + " " + secret;
        }

        private String combine(String prefix, int times) {
            return prefix + ":" + secret + ":" + count + ":" + times;
        }
    }

    public static class PublicNoArgBean {
        public String value() {
            return "created";
        }
    }

    public static class RequiredArgumentsBean {
        private final String name;
        private final int count;

        public RequiredArgumentsBean(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String name() {
            return name;
        }

        public int count() {
            return count;
        }
    }
}
