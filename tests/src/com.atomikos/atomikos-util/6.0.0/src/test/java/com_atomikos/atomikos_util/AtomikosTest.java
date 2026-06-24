/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_atomikos.atomikos_util;

import static org.assertj.core.api.Assertions.assertThat;

import com.atomikos.util.Atomikos;
import org.junit.jupiter.api.Test;

public class AtomikosTest {
    @Test
    void exposesVersionAndEvaluationFlag() {
        String version = Atomikos.VERSION;

        assertThat(version).isNotBlank();
        assertThat(Atomikos.isEvaluationVersion()).isEqualTo(version.endsWith(".EVAL"));
    }
}
