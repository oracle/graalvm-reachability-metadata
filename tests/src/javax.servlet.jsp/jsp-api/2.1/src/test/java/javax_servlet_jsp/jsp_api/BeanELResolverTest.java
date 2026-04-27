/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet_jsp.jsp_api;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.VariableMapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanELResolverTest {

    @Test
    void readsAndWritesPropertyThroughPublicInterfaceOnPackagePrivateBean() {
        BeanELResolver resolver = new BeanELResolver();
        InterfaceBackedBean bean = new InterfaceBackedBean("before");

        ELContext getContext = newContext();
        Object value = resolver.getValue(getContext, bean, "name");

        assertThat(value).isEqualTo("before");
        assertThat(getContext.isPropertyResolved()).isTrue();

        ELContext setContext = newContext();
        resolver.setValue(setContext, bean, "name", "after");

        assertThat(setContext.isPropertyResolved()).isTrue();
        assertThat(bean.getName()).isEqualTo("after");
    }

    @Test
    void readsAndWritesPropertyThroughPublicSuperclassOnPackagePrivateBean() {
        BeanELResolver resolver = new BeanELResolver();
        InheritedNameBean bean = new InheritedNameBean("base");

        ELContext getContext = newContext();
        Object value = resolver.getValue(getContext, bean, "name");

        assertThat(value).isEqualTo("base");
        assertThat(getContext.isPropertyResolved()).isTrue();

        ELContext setContext = newContext();
        resolver.setValue(setContext, bean, "name", "updated");

        assertThat(setContext.isPropertyResolved()).isTrue();
        assertThat(bean.getName()).isEqualTo("updated");
    }

    private static ELContext newContext() {
        return new TestELContext();
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

    static final class TestELContext extends ELContext {
        private final ELResolver elResolver = new BeanELResolver();

        @Override
        public ELResolver getELResolver() {
            return elResolver;
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
