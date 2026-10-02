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

public class BeanMapInnerBeanEntryAnonymous1Test {
    @Test
    void readsBeanPropertyThroughMapEntry() throws Exception {
        ReadableBean bean = new ReadableBean("mapped");
        BeanMap map = new BeanMap(bean);

        assertThat(map.get("value")).isEqualTo("mapped");
    }

    public static class ReadableBean {
        private final String value;

        public ReadableBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
