/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import java.util.SortedMap;

import org.apache.commons.compress.java.util.jar.Pack200;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Pack200$1Test {

    private static final String PACKER_PROPERTY = "java.util.jar.Pack200.Packer";
    private static final String DEFAULT_PACKER_IMPLEMENTATION = "org.apache.commons.compress.harmony.pack200.Pack200PackerAdapter";

    @Test
    void newPackerLoadsTheDefaultImplementationWhenNoOverrideIsConfigured() {
        String previousValue = System.getProperty(PACKER_PROPERTY);

        try {
            System.clearProperty(PACKER_PROPERTY);

            Pack200.Packer packer = Pack200.newPacker();
            SortedMap<String, String> properties = packer.properties();
            properties.put(Pack200.Packer.EFFORT, "0");

            assertThat(packer).isNotNull();
            assertThat(packer.getClass().getName()).isEqualTo(DEFAULT_PACKER_IMPLEMENTATION);
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
}
