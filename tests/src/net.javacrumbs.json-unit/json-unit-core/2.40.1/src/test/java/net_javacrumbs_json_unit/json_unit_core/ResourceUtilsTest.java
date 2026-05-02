/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_javacrumbs_json_unit.json_unit_core;

import net.javacrumbs.jsonunit.core.util.ResourceUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ResourceUtilsTest {

    @Test
    void reportsMissingClasspathResource() {
        assertThatThrownBy(() -> ResourceUtils.resource("json-unit-test-fixtures/missing-resource.json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("resource 'json-unit-test-fixtures/missing-resource.json' not found");
    }
}
