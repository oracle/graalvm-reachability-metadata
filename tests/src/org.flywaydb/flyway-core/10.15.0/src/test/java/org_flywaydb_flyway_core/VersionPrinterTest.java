/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb_flyway_core;

import org.flywaydb.core.internal.license.VersionPrinter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionPrinterTest {

    @Test
    void readsThePackagedFlywayVersion() {
        assertThat(VersionPrinter.getVersion()).isNotBlank();
    }
}
