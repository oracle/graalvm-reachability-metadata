/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_activation_1_1_spec;

import javax.activation.MimetypesFileTypeMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MimetypesFileTypeMapTest {
    @Test
    void resolvesTypesFromBundledDefaults() {
        MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap();

        assertThat(fileTypeMap.getContentType("message.txt")).isEqualTo("text/plain");
        assertThat(fileTypeMap.getContentType("archive.unknown")).isEqualTo("application/octet-stream");
    }
}
