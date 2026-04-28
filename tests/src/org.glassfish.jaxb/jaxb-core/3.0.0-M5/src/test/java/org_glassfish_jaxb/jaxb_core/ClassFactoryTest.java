/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.glassfish.jaxb.core.v2.ClassFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class ClassFactoryTest {
    @AfterEach
    public void clearConstructorCache() {
        ClassFactory.cleanCache();
    }

    @Test
    public void createsInstancesUsingDefaultConstructor() throws Exception {
        ConstructedProduct product = ClassFactory.create0(ConstructedProduct.class);

        assertThat(product.name()).isEqualTo("default-constructor");
        assertThat(product.wasConstructed()).isTrue();
    }

    @Test
    public void invokesNoArgumentStaticFactoryMethod() throws Exception {
        Method factoryMethod = ProductFactory.class.getMethod("createProduct");

        Object product = ClassFactory.create(factoryMethod);

        assertThat(product).isInstanceOf(FactoryProduct.class);
        FactoryProduct factoryProduct = (FactoryProduct) product;
        assertThat(factoryProduct.name()).isEqualTo("static-factory");
    }

    public static final class ConstructedProduct {
        private final String name;
        private final boolean constructed;

        public ConstructedProduct() {
            this.name = "default-constructor";
            this.constructed = true;
        }

        public String name() {
            return name;
        }

        public boolean wasConstructed() {
            return constructed;
        }
    }

    public static final class ProductFactory {
        private ProductFactory() {
        }

        public static FactoryProduct createProduct() {
            return new FactoryProduct("static-factory");
        }
    }

    public static final class FactoryProduct {
        private final String name;

        private FactoryProduct(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }
    }
}
