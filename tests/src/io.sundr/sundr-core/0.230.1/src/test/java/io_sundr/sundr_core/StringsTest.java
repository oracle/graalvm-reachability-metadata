/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.utils.Strings;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class StringsTest {
    @Test
    void loadsClasspathResourceByName() throws IOException {
        String resource = Strings.loadResource("META-INF/maven/io.sundr/sundr-core/pom.properties");

        assertThat(resource)
                .contains("groupId=io.sundr")
                .contains("artifactId=sundr-core");
    }
}
