/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

public class BeanUtilsTest {

    @SuppressWarnings("deprecation")
    @Test
    public void instantiateUsesPublicNoArgConstructor() {
        LegacyInstantiatedBean bean = BeanUtils.instantiate(LegacyInstantiatedBean.class);

        assertThat(bean.getMessage()).isEqualTo("legacy");
    }

    @Test
    public void instantiateClassUsesDeclaredNoArgConstructor() {
        DeclaredNoArgBean bean = BeanUtils.instantiateClass(DeclaredNoArgBean.class);

        assertThat(bean.getMessage()).isEqualTo("declared");
    }

    @Test
    public void instantiateClassWithConstructorInvokesResolvedConstructor() {
        Constructor<SinglePublicConstructorBean> constructor = BeanUtils.getResolvableConstructor(
                SinglePublicConstructorBean.class);

        SinglePublicConstructorBean bean = BeanUtils.instantiateClass(constructor, "spring");

        assertThat(constructor.getParameterTypes()).containsExactly(String.class);
        assertThat(bean.getMessage()).isEqualTo("spring");
    }

    @Test
    public void getResolvableConstructorUsesSingleNonPublicConstructor() {
        Constructor<PrivateConstructorBean> constructor = BeanUtils.getResolvableConstructor(
                PrivateConstructorBean.class);
        PrivateConstructorBean bean = BeanUtils.instantiateClass(constructor);

        assertThat(constructor.getParameterCount()).isZero();
        assertThat(bean.getMessage()).isEqualTo("private");
    }

    @Test
    public void getResolvableConstructorFallsBackToDefaultConstructorWhenMultipleConstructorsExist() {
        Constructor<MultipleConstructorBean> constructor = BeanUtils.getResolvableConstructor(
                MultipleConstructorBean.class);
        MultipleConstructorBean bean = BeanUtils.instantiateClass(constructor);

        assertThat(constructor.getParameterCount()).isZero();
        assertThat(bean.getMessage()).isEqualTo("default");
    }

    @Test
    public void findMethodUsesPublicLookupAndFindDeclaredMethodUsesDeclaredLookup() {
        Method publicMethod = BeanUtils.findMethod(MethodBean.class, "publicMessage");
        Method privateMethod = BeanUtils.findMethod(MethodBean.class, "privateMessage");

        assertThat(publicMethod).isNotNull();
        assertThat(publicMethod.getParameterCount()).isZero();
        assertThat(privateMethod).isNotNull();
        assertThat(privateMethod.getParameterCount()).isZero();
    }

    @Test
    public void findMethodWithMinimalParametersScansPublicAndDeclaredMethods() {
        Method publicMethod = BeanUtils.findMethodWithMinimalParameters(MethodBean.class, "overloadedPublic");
        Method declaredMethod = BeanUtils.findDeclaredMethodWithMinimalParameters(MethodBean.class,
                "overloadedPrivate");

        assertThat(publicMethod).isNotNull();
        assertThat(publicMethod.getParameterCount()).isZero();
        assertThat(declaredMethod).isNotNull();
        assertThat(declaredMethod.getParameterCount()).isZero();
    }

    @Test
    public void findEditorByConventionLoadsAndInstantiatesEditorClass() {
        PropertyEditor editor = BeanUtils.findEditorByConvention(EditableValue.class);

        assertThat(editor).isInstanceOf(EditableValueEditor.class);
        editor.setAsText("converted");
        assertThat(editor.getValue()).isEqualTo(new EditableValue("converted"));
    }

    @Test
    public void copyPropertiesInvokesReadAndWriteMethods() {
        SourceBean source = new SourceBean("copied");
        TargetBean target = new TargetBean();

        BeanUtils.copyProperties(source, target);

        assertThat(target.getValue()).isEqualTo("copied");
    }

    public static class LegacyInstantiatedBean {
        private final String message = "legacy";

        public String getMessage() {
            return message;
        }
    }

    public static class DeclaredNoArgBean {
        private final String message;

        private DeclaredNoArgBean() {
            this.message = "declared";
        }

        public String getMessage() {
            return message;
        }
    }

    public static class SinglePublicConstructorBean {
        private final String message;

        public SinglePublicConstructorBean(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private static class PrivateConstructorBean {
        private final String message;

        private PrivateConstructorBean() {
            this.message = "private";
        }

        public String getMessage() {
            return message;
        }
    }

    public static class MultipleConstructorBean {
        private final String message;

        public MultipleConstructorBean() {
            this.message = "default";
        }

        public MultipleConstructorBean(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class MethodBean {

        public String publicMessage() {
            return "public";
        }

        private String privateMessage() {
            return "private";
        }

        public String overloadedPublic() {
            return "minimal";
        }

        public String overloadedPublic(String value) {
            return value;
        }

        private String overloadedPrivate() {
            return "minimal";
        }

        private String overloadedPrivate(String value) {
            return value;
        }
    }

    public static class EditableValue {
        private final String value;

        public EditableValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof EditableValue)) {
                return false;
            }
            EditableValue that = (EditableValue) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    public static class EditableValueEditor extends PropertyEditorSupport {

        @Override
        public void setAsText(String text) {
            setValue(new EditableValue(text));
        }
    }

    public static class SourceBean {
        private final String value;

        public SourceBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static class TargetBean {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
