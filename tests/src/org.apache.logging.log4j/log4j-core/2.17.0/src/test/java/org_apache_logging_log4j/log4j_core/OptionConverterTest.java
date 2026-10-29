/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.util.Properties;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.util.OptionConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class OptionConverterTest {
    @Test
    void convertsLevelsUsingAnExplicitLevelProvider() {
        assertEquals(Level.WARN, OptionConverter.toLevel("WARN#" + Level.class.getName(), Level.ERROR));
    }

    @Test
    void instantiatesTheClassNamedInAProperty() {
        Properties properties = new Properties();
        properties.setProperty("settings", Settings.class.getName());
        Settings settings = assertInstanceOf(Settings.class,
                OptionConverter.instantiateByKey(properties, "settings", Settings.class, null));
        assertEquals("application", settings.name);
    }

    public static class Settings {
        public final String name = "application";
    }
}
