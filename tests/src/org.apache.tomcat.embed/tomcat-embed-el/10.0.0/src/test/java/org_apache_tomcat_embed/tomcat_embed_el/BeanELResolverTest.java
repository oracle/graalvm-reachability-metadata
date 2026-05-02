/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.ELManager;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanELResolverTest {

    @Test
    void invokesBeanMethodWhenParameterTypesAreProvided() {
        BeanELResolver resolver = new BeanELResolver();

        Object value = resolver.invoke(
                newContext(),
                new MethodTarget(),
                "describeInput",
                new Class<?>[] {String.class},
                new Object[] {"typed"});

        assertThat(value).isEqualTo("input:typed");
    }

    @Test
    void invokesBeanMethodWhenParameterTypesAreInferredFromArguments() {
        BeanELResolver resolver = new BeanELResolver();

        Object value = resolver.invoke(
                newContext(),
                new MethodTarget(),
                "describeInput",
                null,
                new Object[] {"inferred"});

        assertThat(value).isEqualTo("input:inferred");
    }

    @Test
    void readsAndWritesPropertyOnPackagePrivateBeanViaPublicInterfaceMethods() {
        BeanELResolver resolver = new BeanELResolver();
        InterfaceBackedBean bean = new InterfaceBackedBean("before");

        Object value = resolver.getValue(newContext(), bean, "name");

        assertThat(value).isEqualTo("before");

        resolver.setValue(newContext(), bean, "name", "after");

        assertThat(bean.getName()).isEqualTo("after");
    }

    @Test
    void resolvesPropertyTypeOnPackagePrivateBeanViaPublicSuperclassMethods() {
        BeanELResolver resolver = new BeanELResolver();
        InheritedNameBean bean = new InheritedNameBean("base");

        Class<?> propertyType = resolver.getType(newContext(), bean, "name");

        assertThat(propertyType).isEqualTo(String.class);
    }

    private static ELContext newContext() {
        return new ELManager().getELContext();
    }

    public interface NameContract {
        String getName();

        void setName(String value);
    }

    static final class InterfaceBackedBean implements NameContract {
        private String name;

        InterfaceBackedBean(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String value) {
            this.name = value;
        }
    }

    public static class PublicNameBase {
        private String name;

        public PublicNameBase(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String value) {
            this.name = value;
        }
    }

    static final class InheritedNameBean extends PublicNameBase {
        InheritedNameBean(String name) {
            super(name);
        }
    }

    public static final class MethodTarget {
        public String describeInput(String value) {
            return "input:" + value;
        }
    }
}
