/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.verifier.Verifier;
import org.junit.jupiter.api.Test;

public class VerifierDynamicAccessTest {
    @Test
    void localizesVerifierMessagesFromResourceBundles() {
        String message = Verifier.localizeMessage(Verifier.ERR_UNEXPECTED_TEXT, new Object[0]);

        assertThat(message).containsIgnoringCase("unexpected");
    }
}
