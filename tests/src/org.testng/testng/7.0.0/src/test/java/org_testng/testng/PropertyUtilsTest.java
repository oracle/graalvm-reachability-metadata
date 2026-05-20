/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testng.internal.PropertyUtils;

public class PropertyUtilsTest {
    @Test
    void setsBeanPropertyWithRealValue() {
        ConfigurableTestBean bean = new ConfigurableTestBean();

        PropertyUtils.setPropertyRealValue(bean, "threshold", 17);

        assertThat(bean.getThreshold()).isEqualTo(17);
    }

    public static final class ConfigurableTestBean {
        private int threshold;

        public int getThreshold() {
            return threshold;
        }

        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }
    }
}
