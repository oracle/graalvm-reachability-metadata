/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_atomikos.atomikos_util;

import com.atomikos.util.Atomikos;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AtomikosTest {

    @Test
    void versionIsLoadedFromThePackagedPomProperties() {
        String version = Atomikos.VERSION;

        assertThat(version).isNotBlank();
        assertThat(version).isNotEqualTo("UNKNOWN");
        assertThat(version).containsPattern("\\d");
    }

    @Test
    void isEvaluationVersionReflectsTheResolvedVersionSuffix() {
        assertThat(Atomikos.isEvaluationVersion()).isEqualTo(Atomikos.VERSION.endsWith(".EVAL"));
    }
}
