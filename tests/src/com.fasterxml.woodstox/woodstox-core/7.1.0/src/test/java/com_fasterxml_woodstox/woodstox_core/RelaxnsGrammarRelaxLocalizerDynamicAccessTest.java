/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.relaxns.grammar.relax.Localizer;
import org.junit.jupiter.api.Test;

public class RelaxnsGrammarRelaxLocalizerDynamicAccessTest {
    @Test
    void localizesAnyOtherNamespaceWarnings() {
        assertThat(Localizer.localize(Localizer.WRN_ANYOTHER_NAMESPACE_IGNORED, "urn:test:ignored"))
                .contains("cannot be specified")
                .contains("urn:test:ignored");
    }
}
