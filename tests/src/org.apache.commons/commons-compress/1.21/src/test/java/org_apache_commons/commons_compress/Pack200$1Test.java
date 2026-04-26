/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.OutputStream;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import org.apache.commons.compress.java.util.jar.Pack200;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Pack2001Test {

    private static final String PACKER_PROPERTY = "java.util.jar.Pack200.Packer";
    @Test
    void newPackerLoadsTheConfiguredImplementation() {
        String previousValue = System.getProperty(PACKER_PROPERTY);

        try {
            System.setProperty(PACKER_PROPERTY, TestPacker.class.getName());

            Pack200.Packer packer = Pack200.newPacker();
            SortedMap<String, String> properties = packer.properties();
            properties.put(Pack200.Packer.EFFORT, "0");

            assertThat(packer).isInstanceOf(TestPacker.class);
            assertThat(properties).containsEntry(Pack200.Packer.EFFORT, "0");
        } finally {
            restoreSystemProperty(previousValue);
        }
    }

    private static void restoreSystemProperty(String value) {
        if (value == null) {
            System.clearProperty(PACKER_PROPERTY);
            return;
        }

        System.setProperty(PACKER_PROPERTY, value);
    }

    public static final class TestPacker implements Pack200.Packer {

        private final SortedMap<String, String> properties = new TreeMap<>();

        @Override
        public SortedMap<String, String> properties() {
            return properties;
        }

        @Override
        public void pack(JarFile in, OutputStream out) throws IOException {
        }

        @Override
        public void pack(JarInputStream in, OutputStream out) throws IOException {
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
        }
    }
}
