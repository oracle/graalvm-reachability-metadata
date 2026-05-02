/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml.snakeyaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class ConstructorConstructScalarTest {

    @Test
    void constructsRootBeanWhenRootTypeIsProvidedAsClassName() throws ClassNotFoundException {
        Yaml yaml = new Yaml(new Constructor(RootBean.class.getName()));

        RootBean bean = yaml.load(
                """
                name: Example
                quantity: 7
                """);

        assertThat(bean.getName()).isEqualTo("Example");
        assertThat(bean.getQuantity()).isEqualTo(7);
    }

    @Test
    void constructsRootBeanWhenRootTypeClassNameUsesCustomLoaderOptions()
            throws ClassNotFoundException {
        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(RootBean.class.getName(), loaderOptions));

        RootBean bean = yaml.load(
                """
                name: Another example
                quantity: 11
                """);

        assertThat(bean.getName()).isEqualTo("Another example");
        assertThat(bean.getQuantity()).isEqualTo(11);
    }

    @Test
    void constructsTaggedBeanWhenContextClassLoaderCannotResolveTheTagClass() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader rejectingLoader = new ClassLoader(null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (FallbackTaggedBean.class.getName().equals(name)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };

        Thread.currentThread().setContextClassLoader(rejectingLoader);
        try {
            FallbackTaggedBean bean = new Yaml().load(
                    """
                    !!%s
                    name: tagged
                    quantity: 3
                    """.formatted(FallbackTaggedBean.class.getName()));

            assertThat(bean.getName()).isEqualTo("tagged");
            assertThat(bean.getQuantity()).isEqualTo(3);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void constructsCustomScalarWithSingleDeclaredConstructor() {
        SingleIntegerScalar value = new Yaml().loadAs("42", SingleIntegerScalar.class);

        assertThat(value.getValue()).isEqualTo(42);
    }

    @Test
    void constructsCustomScalarWithStringConstructorWhenMultipleDeclaredConstructorsExist() {
        MultiConstructorScalar value = new Yaml().loadAs("007", MultiConstructorScalar.class);

        assertThat(value.getConstructionPath()).isEqualTo("string:007");
    }

    @Test
    void constructsDateSubclassWithPublicLongConstructor() {
        String yaml = "2024-03-01T10:15:30Z";

        Date expected = new Yaml().loadAs(yaml, Date.class);
        TimestampWrapper actual = new Yaml().loadAs(yaml, TimestampWrapper.class);

        assertThat(actual).isInstanceOf(TimestampWrapper.class);
        assertThat(actual.getTime()).isEqualTo(expected.getTime());
    }

    public static final class RootBean {
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

    public static final class FallbackTaggedBean {
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

    public static final class SingleIntegerScalar {
        private final int value;

        private SingleIntegerScalar(Integer value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public static final class MultiConstructorScalar {
        private final String constructionPath;

        private MultiConstructorScalar(Integer value) {
            this.constructionPath = "integer:" + value;
        }

        private MultiConstructorScalar(String value) {
            this.constructionPath = "string:" + value;
        }

        public String getConstructionPath() {
            return constructionPath;
        }
    }

    public static final class TimestampWrapper extends Date {
        public TimestampWrapper(long time) {
            super(time);
        }
    }
}
