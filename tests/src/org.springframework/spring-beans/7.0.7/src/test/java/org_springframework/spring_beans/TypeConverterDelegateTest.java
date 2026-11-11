/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.SimpleTypeConverter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypeConverterDelegateTest {
    @Test
    void convertsCollectionArrayAndScalarToTypedArrays() {
        SimpleTypeConverter converter = new SimpleTypeConverter();
        assertArrayEquals(new Integer[] {3, 7}, converter.convertIfNecessary(List.of("3", "7"), Integer[].class));
        assertArrayEquals(new Integer[] {4, 8}, converter.convertIfNecessary(new String[] {"4", "8"}, Integer[].class));
        assertArrayEquals(new Integer[] {9}, converter.convertIfNecessary(9, Integer[].class));
        assertEquals("token", converter.convertIfNecessary("token", Token.class).value);
        assertEquals(Mode.ACTIVE, converter.convertIfNecessary("ACTIVE", Mode.class));
    }

    @Test
    void convertsEnumCollectionsAndQualifiedRawEnumNames() {
        Settings settings = new Settings();
        BeanWrapperImpl wrapper = new BeanWrapperImpl(settings);
        wrapper.setPropertyValue("modes", "ACTIVE,PASSIVE");
        wrapper.setPropertyValue("mode", Mode.class.getName() + ".PASSIVE");
        assertEquals(List.of(Mode.ACTIVE, Mode.PASSIVE), settings.getModes());
        assertEquals(Mode.PASSIVE, settings.getMode());
    }

    public enum Mode {
        ACTIVE, PASSIVE
    }

    public static class Token {
        private final String value;

        public Token(String value) {
            this.value = value;
        }
    }

    public static class Settings {
        private List<Mode> modes;
        private Enum<?> mode;

        public List<Mode> getModes() {
            return this.modes;
        }

        public void setModes(List<Mode> modes) {
            this.modes = modes;
        }

        public Enum<?> getMode() {
            return this.mode;
        }

        public void setMode(Enum<?> mode) {
            this.mode = mode;
        }
    }
}
