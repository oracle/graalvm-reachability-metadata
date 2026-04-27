/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_aether.aether_impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.aether.internal.impl.slf4j.Slf4jLoggerFactory;
import org.junit.jupiter.api.Test;

public class Slf4jLoggerFactoryTest {
    @Test
    void reportsSlf4jAvailabilityDuringFactoryInitialization() {
        boolean slf4jAvailable = Slf4jLoggerFactory.isSlf4jAvailable();

        assertThat(slf4jAvailable).isTrue();
        assertThat(new Slf4jLoggerFactory()).isNotNull();
    }
}
