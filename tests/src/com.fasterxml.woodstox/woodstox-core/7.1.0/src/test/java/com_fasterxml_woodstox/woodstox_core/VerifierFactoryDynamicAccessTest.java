/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv.org_isorelax.verifier.VerifierFactory;
import org.junit.jupiter.api.Test;

public class VerifierFactoryDynamicAccessTest {
    @Test
    void loadsVerifierFactoriesFromServiceResources() throws Exception {
        VerifierFactory factory = VerifierFactory.newInstance("http://www.w3.org/2001/XMLSchema");

        assertThat(factory).isNotNull();
        assertThat(factory.getClass().getName()).contains("XSFactoryImpl");
    }
}
