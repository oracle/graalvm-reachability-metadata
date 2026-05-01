/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_el.el_api;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.VariableMapper;

import java.beans.FeatureDescriptor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanELResolverTest {
    @Test
    void readsAndWritesPublicBeanProperties() {
        BeanELResolver resolver = new BeanELResolver();
        TestELContext context = new TestELContext(resolver);
        MutableBean bean = new MutableBean("initial");

        Object value = resolver.getValue(context, bean, "name");

        assertThat(value).isEqualTo("initial");
        assertThat(context.isPropertyResolved()).isTrue();

        context.setPropertyResolved(false);
        resolver.setValue(context, bean, "name", "updated");

        assertThat(bean.getName()).isEqualTo("updated");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void resolvesPropertyAccessorsDeclaredOnPublicInterface() {
        BeanELResolver resolver = new BeanELResolver();
        TestELContext context = new TestELContext(resolver);
        InterfaceBackedBean bean = new InterfaceBackedBean("interface value");

        Object value = resolver.getValue(context, bean, "label");

        assertThat(value).isEqualTo("interface value");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void resolvesPropertyAccessorsDeclaredOnPublicSuperclass() {
        BeanELResolver resolver = new BeanELResolver();
        TestELContext context = new TestELContext(resolver);
        InheritedBean bean = new InheritedBean("parent value");

        Object value = resolver.getValue(context, bean, "description");

        assertThat(value).isEqualTo("parent value");
        assertThat(context.isPropertyResolved()).isTrue();

        context.setPropertyResolved(false);
        resolver.setValue(context, bean, "description", "updated parent value");

        assertThat(bean.getDescription()).isEqualTo("updated parent value");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void reportsCommonPropertyTypeAndFeatureDescriptors() {
        BeanELResolver resolver = new BeanELResolver();
        TestELContext context = new TestELContext(resolver);
        MutableBean bean = new MutableBean("descriptor value");

        Class<?> commonType = resolver.getCommonPropertyType(context, bean);
        Iterator<FeatureDescriptor> descriptors = resolver.getFeatureDescriptors(context, bean);
        List<String> descriptorNames = new ArrayList<>();
        while (descriptors.hasNext()) {
            descriptorNames.add(descriptors.next().getName());
        }

        assertThat(commonType).isEqualTo(Object.class);
        assertThat(descriptorNames).contains("name");
    }

    public static final class MutableBean {
        private String name;

        public MutableBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public interface Labeled {
        String getLabel();
    }

    static final class InterfaceBackedBean implements Labeled {
        private final String label;

        InterfaceBackedBean(String label) {
            this.label = label;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }

    public static class PublicParentBean {
        private String description;

        public PublicParentBean(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    static final class InheritedBean extends PublicParentBean {
        InheritedBean(String description) {
            super(description);
        }
    }

    private static final class TestELContext extends ELContext {
        private final ELResolver resolver;

        TestELContext(ELResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public ELResolver getELResolver() {
            return resolver;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return null;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return null;
        }
    }
}
