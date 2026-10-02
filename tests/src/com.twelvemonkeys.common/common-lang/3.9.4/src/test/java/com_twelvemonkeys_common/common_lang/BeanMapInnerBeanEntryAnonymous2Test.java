/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twelvemonkeys_common.common_lang;

import com.twelvemonkeys.util.BeanMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanMapInnerBeanEntryAnonymous2Test {
    @Test
    void updatesBeanPropertyThroughMapEntry() throws Exception {
        WritableBean bean = new WritableBean();
        bean.setValue("before");
        BeanMap map = new BeanMap(bean);

        assertThat(map.put("value", "after")).isEqualTo("before");
        assertThat(bean.getValue()).isEqualTo("after");
    }

    public static class WritableBean {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
