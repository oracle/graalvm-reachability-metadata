/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicSerializerFactoryTest {
    @Test
    void createsAStandardSerializerFromTheLookupTable() throws Exception {
        assertThat(new ObjectMapper().writeValueAsString(URI.create("https://example.invalid/value")))
            .isEqualTo("\"https://example.invalid/value\"");
    }
}
