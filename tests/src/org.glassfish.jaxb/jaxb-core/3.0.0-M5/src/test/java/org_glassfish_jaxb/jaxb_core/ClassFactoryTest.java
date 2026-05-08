/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_core;

import java.lang.reflect.Method;

import org.glassfish.jaxb.core.v2.ClassFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassFactoryTest {
    @Test
    void createsInstanceUsingDefaultConstructor() throws Exception {
        ClassFactory.cleanCache();

        Constructible created = ClassFactory.create0(Constructible.class);

        assertThat(created.value()).isEqualTo("constructed");
    }

    @Test
    void invokesStaticFactoryMethod() throws Exception {
        Method method = Factory.class.getMethod("createConstructible");

        Object created = ClassFactory.create(method);

        assertThat(created).isInstanceOf(Constructible.class);
        assertThat(((Constructible) created).value()).isEqualTo("from-factory");
    }

    public static class Constructible {
        private final String value;

        public Constructible() {
            this("constructed");
        }

        private Constructible(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static class Factory {
        public static Constructible createConstructible() {
            return new Constructible("from-factory");
        }
    }
}
