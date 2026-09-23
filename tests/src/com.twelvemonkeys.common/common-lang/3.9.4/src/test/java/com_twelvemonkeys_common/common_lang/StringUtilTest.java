/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twelvemonkeys_common.common_lang;

import com.twelvemonkeys.lang.StringUtil;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

public class StringUtilTest {
    @Test
    void resolvesColorConstantsByExactAndNormalizedNames() {
        assertThat(StringUtil.toColor("darkGray")).isEqualTo(Color.darkGray);
        assertThat(StringUtil.toColor("RED")).isEqualTo(Color.red);
    }

    @Test
    void rendersBeanGettersDeeply() {
        String rendered = StringUtil.deepToString(new DescribedBean("sample", true), true, 1);

        assertThat(rendered)
                .contains(DescribedBean.class.getName())
                .contains("name=sample")
                .contains("active=true");
    }

    public static class DescribedBean {
        private final String name;
        private final boolean active;

        public DescribedBean(String name, boolean active) {
            this.name = name;
            this.active = active;
        }

        public String getName() {
            return name;
        }

        public boolean isActive() {
            return active;
        }
    }
}
