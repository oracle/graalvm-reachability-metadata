/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.reflectasm.ConstructorAccess;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ConstructorAccessTest {
    @Test
    void generatedConstructorAccessorCreatesPublicNoArgTypes() {
        try {
            ConstructorAccess<ConstructedBean> access = ConstructorAccess.get(ConstructedBean.class);

            ConstructedBean instance = access.newInstance();

            assertThat(access.isNonStaticMemberClass()).isFalse();
            assertThat(instance.getName()).isEqualTo("constructed");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void generatedConstructorAccessorCreatesNonStaticMemberTypes() {
        try {
            ConstructorAccess<Outer.ConstructedInner> access = ConstructorAccess.get(Outer.ConstructedInner.class);
            Outer outer = new Outer("outer");

            Outer.ConstructedInner instance = access.newInstance(outer);

            assertThat(access.isNonStaticMemberClass()).isTrue();
            assertThat(instance.describe()).isEqualTo("outer-inner");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class ConstructedBean {
        private final String name;

        public ConstructedBean() {
            name = "constructed";
        }

        public String getName() {
            return name;
        }
    }

    public static class Outer {
        private final String prefix;

        public Outer(String prefix) {
            this.prefix = prefix;
        }

        public class ConstructedInner {
            public ConstructedInner() {
            }

            public String describe() {
                return prefix + "-inner";
            }
        }
    }
}
