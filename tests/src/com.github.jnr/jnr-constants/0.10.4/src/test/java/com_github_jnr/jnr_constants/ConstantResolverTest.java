/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_constants;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jnr.constants.platform.Signal;
import org.junit.jupiter.api.Test;

public class ConstantResolverTest {
    @Test
    void resolvesPlatformSignalByNumericValue() {
        Signal interrupt = Signal.SIGINT;

        assertTrue(interrupt.defined());
        assertSame(interrupt, Signal.valueOf(interrupt.longValue()));
    }
}
