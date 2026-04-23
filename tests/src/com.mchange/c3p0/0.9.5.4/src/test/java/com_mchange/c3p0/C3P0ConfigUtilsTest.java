/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.cfg.C3P0ConfigUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class C3P0ConfigUtilsTest {
    @Test
    void extractsHardcodedDefaults() {
        Map<?, ?> defaults = C3P0ConfigUtils.extractHardcodedC3P0Defaults();

        assertThat(defaults).containsKeys("maxPoolSize", "minPoolSize", "numHelperThreads");
    }
}
