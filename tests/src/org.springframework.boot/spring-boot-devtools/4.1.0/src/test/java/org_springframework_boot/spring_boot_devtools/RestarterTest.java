/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_devtools;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

import org.springframework.boot.devtools.restart.RestartInitializer;
import org.springframework.boot.devtools.restart.Restarter;

public class RestarterTest {

    @Test
    void initializePreInitializesLeakyClasses() {
        final TestRestarter restarter = new TestRestarter();

        assertDoesNotThrow(() -> restarter.initializeWithoutRestart());
    }

    @Test
    void stopCleansKnownFrameworkCaches() {
        final TestRestarter restarter = new TestRestarter();

        assertDoesNotThrow(() -> restarter.stopRestarter());
    }

    private static final class TestRestarter extends Restarter {

        private TestRestarter() {
            super(new Thread(), new String[0], false, RestartInitializer.NONE);
        }

        private void initializeWithoutRestart() {
            initialize(false);
        }

        private void stopRestarter() throws Exception {
            stop();
        }

    }

}
