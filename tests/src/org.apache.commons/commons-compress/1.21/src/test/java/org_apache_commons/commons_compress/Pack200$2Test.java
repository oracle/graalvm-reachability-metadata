/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.JarOutputStream;

import org.apache.commons.compress.java.util.jar.Pack200;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Pack2002Test {

    private static final String UNPACKER_PROPERTY = "java.util.jar.Pack200.Unpacker";

    @Test
    void newUnpackerLoadsTheConfiguredImplementation() {
        String previousValue = System.getProperty(UNPACKER_PROPERTY);

        try {
            System.setProperty(UNPACKER_PROPERTY, TestUnpacker.class.getName());

            Pack200.Unpacker unpacker = Pack200.newUnpacker();
            SortedMap<String, String> properties = unpacker.properties();
            properties.put(Pack200.Unpacker.DEFLATE_HINT, Pack200.Unpacker.TRUE);

            assertThat(unpacker).isInstanceOf(TestUnpacker.class);
            assertThat(properties).containsEntry(Pack200.Unpacker.DEFLATE_HINT, Pack200.Unpacker.TRUE);
        } finally {
            restoreSystemProperty(previousValue);
        }
    }

    private static void restoreSystemProperty(String value) {
        if (value == null) {
            System.clearProperty(UNPACKER_PROPERTY);
            return;
        }

        System.setProperty(UNPACKER_PROPERTY, value);
    }

    public static final class TestUnpacker implements Pack200.Unpacker {

        private final SortedMap<String, String> properties = new TreeMap<>();

        @Override
        public SortedMap<String, String> properties() {
            return properties;
        }

        @Override
        public void unpack(InputStream in, JarOutputStream out) throws IOException {
        }

        @Override
        public void unpack(File in, JarOutputStream out) throws IOException {
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
        }
    }
}
