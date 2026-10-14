/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_constants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jnr.constants.Constant;
import jnr.constants.ConstantSet;
import org.junit.jupiter.api.Test;

public class ConstantSetTest {
    @Test
    void resolvesSignalConstantsAndTheirRange() {
        ConstantSet signals = ConstantSet.getConstantSet("Signal");

        assertNotNull(signals);
        Constant interrupt = signals.getConstant("SIGINT");
        assertNotNull(interrupt);
        assertEquals("SIGINT", interrupt.name());
        assertEquals(2L, signals.getValue("SIGINT"));
        assertSame(interrupt, signals.getConstant(interrupt.longValue()));
        assertEquals("SIGINT", signals.getName(interrupt.intValue()));
        assertTrue(signals.minValue() <= interrupt.longValue());
        assertTrue(signals.maxValue() >= interrupt.longValue());
    }
}
