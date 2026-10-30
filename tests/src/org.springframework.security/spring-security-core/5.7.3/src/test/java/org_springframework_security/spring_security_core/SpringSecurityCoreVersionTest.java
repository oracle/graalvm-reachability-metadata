/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.SpringSecurityCoreVersion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpringSecurityCoreVersionTest {
    @Test
    void reportsVersionAndChecksFrameworkCompatibilityOnInitialization() {
        Logger logger = Logger.getLogger(SpringSecurityCoreVersion.class.getName());
        Level originalLevel = logger.getLevel();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StreamHandler handler = new StreamHandler(output, new SimpleFormatter());
        logger.setLevel(Level.INFO);
        handler.setLevel(Level.INFO);
        logger.addHandler(handler);
        try {
            String version = SpringSecurityCoreVersion.getVersion();
            handler.flush();

            assertNotNull(version);
            assertFalse(version.isBlank());
            assertTrue(output.toString(StandardCharsets.UTF_8).contains(
                    "You are running with Spring Security Core " + version));
        } finally {
            logger.removeHandler(handler);
            handler.close();
            logger.setLevel(originalLevel);
        }
    }
}
