/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.app.FieldMethodizer;
import org.apache.velocity.runtime.RuntimeConstants;
import org.junit.jupiter.api.Test;

public class FieldMethodizerTest {
    @Test
    void exposesPublicStaticFieldsAddedByClassName() throws Exception {
        final FieldMethodizer methodizer = new FieldMethodizer();

        methodizer.addObject(RuntimeConstants.class.getName());

        assertThat(methodizer.get("RUNTIME_LOG_WARN_STACKTRACE")).isEqualTo(RuntimeConstants.RUNTIME_LOG_WARN_STACKTRACE);
        assertThat(methodizer.get("VM_LIBRARY")).isEqualTo(RuntimeConstants.VM_LIBRARY);
        assertThat(methodizer.get("DOES_NOT_EXIST")).isNull();
    }
}
