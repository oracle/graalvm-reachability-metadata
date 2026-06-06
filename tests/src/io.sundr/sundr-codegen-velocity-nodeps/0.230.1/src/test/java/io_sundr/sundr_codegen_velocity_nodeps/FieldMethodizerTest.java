/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.velocity.app.FieldMethodizer;
import io.sundr.deps.org.apache.velocity.runtime.RuntimeConstants;
import org.junit.jupiter.api.Test;

public class FieldMethodizerTest {
    @Test
    void exposesPublicStaticFieldsByName() throws Exception {
        FieldMethodizer methodizer = new FieldMethodizer();

        methodizer.addObject(RuntimeConstants.class.getName());

        assertThat(methodizer.get("RUNTIME_LOG")).isEqualTo(RuntimeConstants.RUNTIME_LOG);
        assertThat(methodizer.get("NUMBER_OF_PARSERS")).isEqualTo(RuntimeConstants.NUMBER_OF_PARSERS);
        assertThat(methodizer.get("missingField")).isNull();
    }
}
