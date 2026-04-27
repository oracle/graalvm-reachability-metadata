/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.velocity.app.FieldMethodizer;
import org.apache.velocity.runtime.RuntimeConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldMethodizerTest {
    @Test
    void resolvesPublicStaticFieldsAddedByClassName() throws Exception {
        FieldMethodizer methodizer = new FieldMethodizer();

        methodizer.addObject(RuntimeConstants.class.getName());

        assertThat(methodizer.get("RUNTIME_LOG")).isEqualTo(RuntimeConstants.RUNTIME_LOG);
        assertThat(methodizer.get("VM_LIBRARY")).isEqualTo(RuntimeConstants.VM_LIBRARY);
        assertThat(methodizer.get("UNKNOWN_FIELD")).isNull();
    }
}
