/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.org_apache_commons_compress;

import org.apache.commons.compress.java.util.jar.Pack200;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Pack200Test {

    private static final String UNPACKER_PROVIDER_PROPERTY = "java.util.jar.Pack200.Unpacker";

    @Test
    void createsDefaultUnpackerThroughProviderLookup() {
        String originalUnpackerProvider = System.clearProperty(UNPACKER_PROVIDER_PROPERTY);
        try {
            Pack200.Unpacker unpacker = Pack200.newUnpacker();

            assertThat(unpacker).isNotNull();
            assertThat(unpacker.properties()).isEmpty();
        } finally {
            restoreProperty(UNPACKER_PROVIDER_PROPERTY, originalUnpackerProvider);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
