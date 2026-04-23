/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.verifier.identity.IDConstraintChecker;
import org.junit.jupiter.api.Test;

public class IDConstraintCheckerDynamicAccessTest {
    @Test
    void localizesIdentityConstraintMessagesFromResourceBundles() {
        String message = IDConstraintChecker.localizeMessage(
                IDConstraintChecker.ERR_NOT_UNIQUE,
                new Object[]{"urn:test", "book-id"});

        assertThat(message).contains("book-id");
    }
}
