/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.msgpack.template.builder.beans.IntrospectionException;
import org.msgpack.template.builder.beans.PropertyChangeListener;
import org.msgpack.template.builder.beans.PropertyDescriptor;
import org.msgpack.template.builder.beans.PropertyEditor;

public class PropertyDescriptorTest {
    @Test
    void locatesReadAndWriteMethodsByConventionalBeanNames() throws IntrospectionException {
        final PropertyDescriptor descriptor = new PropertyDescriptor("name", NamedBean.class);

        assertThat(descriptor.getReadMethod().getName()).isEqualTo("getName");
        assertThat(descriptor.getWriteMethod().getName()).isEqualTo("setName");
        assertThat(descriptor.getPropertyType()).isSameAs(String.class);
    }

    @Test
    void locatesSetterFromDeclaredMethodsWhenNoGetterIsConfigured() throws IntrospectionException {
        final PropertyDescriptor descriptor = new PropertyDescriptor(
                "counter", WriteOnlyBean.class, null, "setCounter");

        assertThat(descriptor.getReadMethod()).isNull();
        assertThat(descriptor.getWriteMethod().getName()).isEqualTo("setCounter");
        assertThat(descriptor.getPropertyType()).isSameAs(Integer.TYPE);
    }

    @Test
    void createsPropertyEditorWithBeanConstructorWhenAvailable() throws IntrospectionException {
        final NamedBean bean = new NamedBean();
        final PropertyDescriptor descriptor = new PropertyDescriptor("name", NamedBean.class);
        descriptor.setPropertyEditorClass(BeanAwareEditor.class);

        final PropertyEditor editor = descriptor.createPropertyEditor(bean);

        assertThat(editor).isInstanceOf(BeanAwareEditor.class);
        assertThat(editor.getValue()).isSameAs(bean);
    }

    @Test
    void createsPropertyEditorWithNoArgumentConstructorAsFallback() throws IntrospectionException {
        final PropertyDescriptor descriptor = new PropertyDescriptor("name", NamedBean.class);
        descriptor.setPropertyEditorClass(NoArgumentEditor.class);

        final PropertyEditor editor = descriptor.createPropertyEditor(new NamedBean());

        assertThat(editor).isInstanceOf(NoArgumentEditor.class);
        assertThat(editor.getValue()).isEqualTo("created");
    }

    public static final class NamedBean {
        private String name;

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static final class WriteOnlyBean {
        private int counter;

        public void setCounter(int counter) {
            this.counter = counter;
        }
    }

    public abstract static class BaseEditor implements PropertyEditor {
        private Object value;

        protected BaseEditor(Object value) {
            this.value = value;
        }

        @Override
        public void setAsText(String text) throws IllegalArgumentException {
            this.value = text;
        }

        @Override
        public String[] getTags() {
            return new String[0];
        }

        @Override
        public String getJavaInitializationString() {
            return String.valueOf(this.value);
        }

        @Override
        public String getAsText() {
            return String.valueOf(this.value);
        }

        @Override
        public void setValue(Object value) {
            this.value = value;
        }

        @Override
        public Object getValue() {
            return this.value;
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
        }

        @Override
        public boolean supportsCustomEditor() {
            return false;
        }

        @Override
        public boolean isPaintable() {
            return false;
        }
    }

    public static final class BeanAwareEditor extends BaseEditor {
        public BeanAwareEditor(Object bean) {
            super(bean);
        }
    }

    public static final class NoArgumentEditor extends BaseEditor {
        public NoArgumentEditor() {
            super("created");
        }
    }
}
