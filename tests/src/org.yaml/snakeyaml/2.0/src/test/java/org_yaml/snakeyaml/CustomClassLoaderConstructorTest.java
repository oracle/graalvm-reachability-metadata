/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml.snakeyaml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;

public class CustomClassLoaderConstructorTest {

    @Test
    void resolvesTaggedBeanWithProvidedClassLoaderWhenContextLoaderRejectsIt() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader resolvingClassLoader =
                new ResolvingClassLoader(CustomClassLoaderConstructorTest.class.getClassLoader());
        ClassLoader rejectingContextClassLoader = new RejectingClassLoader(
                CustomClassLoaderConstructorTest.class.getClassLoader(),
                TaggedBean.class.getName());
        Yaml yaml = new Yaml(new CustomClassLoaderConstructor(resolvingClassLoader));

        Thread.currentThread().setContextClassLoader(rejectingContextClassLoader);
        try {
            TaggedBean bean = yaml.load(
                    """
                    !!%s
                    name: Example
                    quantity: 7
                    """.formatted(TaggedBean.class.getName()));

            assertThat(bean.getName()).isEqualTo("Example");
            assertThat(bean.getQuantity()).isEqualTo(7);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class ResolvingClassLoader extends ClassLoader {
        private ResolvingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            return super.loadClass(name, resolve);
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingClassLoader(ClassLoader parent, String rejectedClassName) {
            super(parent);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }

    public static final class TaggedBean {
        private String name;
        private int quantity;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
