/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.impl.launcher.commands.VersionCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class VersionCommandTest {

    @Test
    void getVersionReadsBundledVersionResource() {
        String version = VersionCommand.getVersion();

        assertNotNull(version);
        assertFalse(version.isBlank());
    }
}
