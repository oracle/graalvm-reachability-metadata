/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanUtilsTest {

    @SuppressWarnings("deprecation")
    @Test
    void instantiateUsesPublicNoArgConstructor() {
        PublicDefaultConstructorBean bean = BeanUtils.instantiate(PublicDefaultConstructorBean.class);

        assertThat(bean.created).isTrue();
    }

    @Test
    void instantiateClassUsesDeclaredConstructor() {
        PackagePrivateDefaultConstructorBean bean =
                BeanUtils.instantiateClass(PackagePrivateDefaultConstructorBean.class);

        assertThat(bean.value).isEqualTo("created");
    }

    @Test
    void instantiateClassInvokesConstructorWithDefaultPrimitiveValues() throws Exception {
        Constructor<PrimitiveConstructorBean> constructor =
                PrimitiveConstructorBean.class.getDeclaredConstructor(int.class, String.class);

        PrimitiveConstructorBean bean = BeanUtils.instantiateClass(constructor, null, "spring");

        assertThat(bean.number).isZero();
        assertThat(bean.name).isEqualTo("spring");
    }

    @Test
    void getResolvableConstructorFindsSinglePublicConstructor() {
        Constructor<SinglePublicConstructorBean> constructor =
                BeanUtils.getResolvableConstructor(SinglePublicConstructorBean.class);

        assertThat(constructor.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void getResolvableConstructorFindsSinglePrivateConstructor() {
        Constructor<SinglePrivateConstructorBean> constructor =
                BeanUtils.getResolvableConstructor(SinglePrivateConstructorBean.class);

        assertThat(constructor.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void getResolvableConstructorFallsBackToDefaultConstructor() {
        Constructor<DefaultConstructorPreferredBean> constructor =
                BeanUtils.getResolvableConstructor(DefaultConstructorPreferredBean.class);

        assertThat(constructor.getParameterCount()).isZero();
    }

    @Test
    void findMethodReturnsPublicMethod() {
        Method method = BeanUtils.findMethod(PublicMethodBean.class, "echo", String.class);

        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("echo");
    }

    @Test
    void findDeclaredMethodReturnsNonPublicMethod() {
        Method method = BeanUtils.findDeclaredMethod(DeclaredMethodBean.class, "hidden", String.class);

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(DeclaredMethodBean.class);
    }

    @Test
    void findMethodWithMinimalParametersReturnsPublicOverload() {
        Method method = BeanUtils.findMethodWithMinimalParameters(PublicMethodBean.class, "overloaded");

        assertThat(method).isNotNull();
        assertThat(method.getParameterCount()).isZero();
    }

    @Test
    void findDeclaredMethodWithMinimalParametersReturnsNonPublicOverload() {
        Method method = BeanUtils.findDeclaredMethodWithMinimalParameters(DeclaredMethodBean.class, "hiddenOverload");

        assertThat(method).isNotNull();
        assertThat(method.getParameterCount()).isZero();
    }

    @Test
    void findEditorByConventionLoadsAndInstantiatesEditor() {
        Class<?> editorClass = EditorBackedValueEditor.class;

        PropertyEditor editor = BeanUtils.findEditorByConvention(EditorBackedValue.class);

        assertThat(editor).isInstanceOf(editorClass);
    }

    @Test
    void copyPropertiesInvokesGetterAndSetter() {
        SourceBean source = new SourceBean();
        source.setName("spring");
        TargetBean target = new TargetBean();

        BeanUtils.copyProperties(source, target);

        assertThat(target.getName()).isEqualTo("spring");
    }

    public static class PublicDefaultConstructorBean {
        private final boolean created;

        public PublicDefaultConstructorBean() {
            this.created = true;
        }
    }

    static class PackagePrivateDefaultConstructorBean {
        private final String value;

        PackagePrivateDefaultConstructorBean() {
            this.value = "created";
        }
    }

    static class PrimitiveConstructorBean {
        private final int number;
        private final String name;

        PrimitiveConstructorBean(int number, String name) {
            this.number = number;
            this.name = name;
        }
    }

    public static class SinglePublicConstructorBean {
        public SinglePublicConstructorBean(String name) {
        }
    }

    private static class SinglePrivateConstructorBean {
        private SinglePrivateConstructorBean(String name) {
        }
    }

    public static class DefaultConstructorPreferredBean {
        public DefaultConstructorPreferredBean() {
        }

        public DefaultConstructorPreferredBean(String name) {
        }
    }

    public static class PublicMethodBean {
        public String echo(String input) {
            return input;
        }

        public String overloaded() {
            return "none";
        }

        public String overloaded(String value) {
            return value;
        }
    }

    static class DeclaredMethodBean {
        private String hidden(String input) {
            return input;
        }

        private String hiddenOverload() {
            return "none";
        }

        private String hiddenOverload(String value) {
            return value;
        }
    }

    public static class EditorBackedValue {
    }

    public static class EditorBackedValueEditor extends PropertyEditorSupport {
    }

    public static class SourceBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class TargetBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
