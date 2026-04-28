/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_crypto;

import java.util.Properties;

import org.apache.commons.crypto.utils.Utils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsTest {
    @Test
    void returnsDefaultProperties() {
        Properties properties = Utils.getDefaultProperties();

        assertThat(properties).containsEntry("java.version", System.getProperty("java.version"));
    }
}
