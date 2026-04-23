/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.cfg.C3P0ConfigXmlUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class C3P0ConfigXmlUtilsTest {
    @Test
    void returnsNullWhenDefaultXmlConfigIsAbsent() throws Exception {
        assertThat(C3P0ConfigXmlUtils.extractXmlConfigFromDefaultResource(false)).isNull();
    }
}
