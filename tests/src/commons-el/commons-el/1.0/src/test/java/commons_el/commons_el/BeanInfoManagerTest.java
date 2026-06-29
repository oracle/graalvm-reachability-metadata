/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_el.commons_el;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import org.apache.commons.el.BeanInfoManager;
import org.apache.commons.el.BeanInfoProperty;
import org.apache.commons.el.Logger;
import org.junit.jupiter.api.Test;

public class BeanInfoManagerTest {
    @Test
    public void resolvesPublicInterfaceAccessorsForNonPublicBeanImplementations() throws Exception {
        Logger logger = new Logger(new PrintStream(OutputStream.nullOutputStream()));

        BeanInfoProperty property = BeanInfoManager.getBeanInfoProperty(
                InterfaceBackedTopLevelBean.class, "name", logger);

        assertThat(property).isNotNull();
        assertThat(property.getPropertyDescriptor().getName()).isEqualTo("name");
        assertPublicNamedBeanMethod(property.getReadMethod(), "getName");
        assertPublicNamedBeanMethod(property.getWriteMethod(), "setName");
    }

    @Test
    public void preservesAccessorsWhenNoPublicDeclaringClassDefinesTheProperty() throws Exception {
        Logger logger = new Logger(new PrintStream(OutputStream.nullOutputStream()));

        BeanInfoProperty property = BeanInfoManager.getBeanInfoProperty(
                NonPublicStandaloneBean.class, "code", logger);

        assertThat(property).isNotNull();
        assertThat(property.getPropertyDescriptor().getName()).isEqualTo("code");
        assertPackagePrivateBeanMethod(property.getReadMethod(), "getCode");
        assertPackagePrivateBeanMethod(property.getWriteMethod(), "setCode");
    }

    @Test
    public void resolvesPublicSuperclassAccessorsForNonPublicBeanSubclasses() throws Exception {
        Logger logger = new Logger(new PrintStream(OutputStream.nullOutputStream()));

        BeanInfoProperty property = BeanInfoManager.getBeanInfoProperty(
                SubclassBackedBean.class, "name", logger);

        assertThat(property).isNotNull();
        assertThat(property.getPropertyDescriptor().getName()).isEqualTo("name");
        assertPublicSuperclassMethod(property.getReadMethod(), "getName");
        assertPublicSuperclassMethod(property.getWriteMethod(), "setName");
    }

    private static void assertPublicNamedBeanMethod(Method method, String name) {
        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo(name);
        assertThat(method.getDeclaringClass()).isEqualTo(NamedBean.class);
    }

    private static void assertPublicSuperclassMethod(Method method, String name) {
        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo(name);
        assertThat(method.getDeclaringClass()).isEqualTo(PublicNamedBean.class);
    }

    private static void assertPackagePrivateBeanMethod(Method method, String name) {
        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo(name);
        assertThat(method.getDeclaringClass()).isEqualTo(NonPublicStandaloneBean.class);
    }

    public interface NamedBean {
        String getName();

        void setName(String name);
    }

    static final class NonPublicStandaloneBean {
        private String code;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    public static class PublicNamedBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    static final class SubclassBackedBean extends PublicNamedBean {
        @Override
        public String getName() {
            return super.getName();
        }

        @Override
        public void setName(String name) {
            super.setName(name);
        }
    }
}

final class InterfaceBackedTopLevelBean implements BeanInfoManagerTest.NamedBean {
    private String name;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }
}
