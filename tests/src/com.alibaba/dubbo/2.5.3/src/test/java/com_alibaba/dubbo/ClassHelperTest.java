/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.utils.ClassHelper;
import org.junit.jupiter.api.Test;

public class ClassHelperTest {
    @Test
    void resolvesSourceAndInternalArrayNames() throws Exception {
        assertThat(ClassHelper.forName("java.lang.String")).isEqualTo(String.class);
        assertThat(ClassHelper.forName("java.lang.String[]")).isEqualTo(String[].class);
        assertThat(ClassHelper.forName("[[Ljava.lang.String;"))
                .isEqualTo(String[][].class);
    }
}
