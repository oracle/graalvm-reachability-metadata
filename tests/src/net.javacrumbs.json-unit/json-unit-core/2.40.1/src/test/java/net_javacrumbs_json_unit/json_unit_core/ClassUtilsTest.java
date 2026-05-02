/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_javacrumbs_json_unit.json_unit_core;

import net.javacrumbs.jsonunit.core.Configuration;
import net.javacrumbs.jsonunit.core.internal.Diff;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {

    @Test
    void probesClasspathWhenComparingJsonDocuments() {
        String previousLibraries = System.setProperty("json-unit.libraries", "json.org");
        try {
            Diff diff = Diff.create(
                    "{\"name\":\"json-unit\",\"values\":[1,2,3]}",
                    "{\"values\":[1,2,3],\"name\":\"json-unit\"}",
                    "actual",
                    "",
                    Configuration.empty());

            assertThat(diff.similar()).isTrue();
            assertThat(diff.differences()).isEqualTo("JSON documents have the same value.");
        } finally {
            if (previousLibraries == null) {
                System.clearProperty("json-unit.libraries");
            } else {
                System.setProperty("json-unit.libraries", previousLibraries);
            }
        }
    }
}
