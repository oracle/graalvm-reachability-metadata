/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.reflectasm.FieldAccess;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class FieldAccessTest {
    @Test
    void generatedFieldAccessorReadsAndWritesPublicFields() {
        try {
            FieldAccess access = FieldAccess.get(AccessedBean.class);
            AccessedBean bean = new AccessedBean();

            int countIndex = access.getIndex("count");
            int nameIndex = access.getIndex("name");
            int activeIndex = access.getIndex("active");

            access.setInt(bean, countIndex, 42);
            access.set(bean, nameIndex, "reflectasm");
            access.setBoolean(bean, activeIndex, true);

            assertThat(access.getFieldNames()).containsExactlyInAnyOrder("count", "name", "active");
            assertThat(access.getFieldTypes()).containsExactlyInAnyOrder(int.class, String.class, boolean.class);
            assertThat(access.getFieldCount()).isEqualTo(3);
            assertThat(access.getInt(bean, countIndex)).isEqualTo(42);
            assertThat(access.get(bean, nameIndex)).isEqualTo("reflectasm");
            assertThat(access.getBoolean(bean, activeIndex)).isTrue();
            assertThat(bean.privateValue).isEqualTo("not exposed");
            assertThat(AccessedBean.staticValue).isEqualTo("static not exposed");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class AccessedBean {
        public int count;
        public String name;
        public boolean active;
        public static final String staticValue = "static not exposed";
        private String privateValue = "not exposed";
    }
}
