/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.msgpack.template.builder.BeansFieldEntry;
import org.msgpack.template.builder.beans.PropertyDescriptor;

public class BeansFieldEntryTest {
    @Test
    void getsAndSetsBeanPropertyThroughDescriptorMethods() throws Exception {
        final PropertyDescriptor descriptor = new PropertyDescriptor("name", MutableBean.class);
        final BeansFieldEntry fieldEntry = new BeansFieldEntry(descriptor);
        final MutableBean bean = new MutableBean();

        fieldEntry.set(bean, "msgpack");

        assertThat(fieldEntry.get(bean)).isEqualTo("msgpack");
        assertThat(bean.getName()).isEqualTo("msgpack");
    }

    public static final class MutableBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
