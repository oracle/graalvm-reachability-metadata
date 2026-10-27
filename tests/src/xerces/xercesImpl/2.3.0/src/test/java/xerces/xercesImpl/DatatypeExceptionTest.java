/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xercesImpl;

import org.apache.xerces.impl.dv.DatatypeException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class DatatypeExceptionTest {
    @Test
    void formatsSchemaDatatypeErrorMessage() {
        Object[] arguments = {"not-a-number", "integer"};
        DatatypeException exception = new DatatypeException("cvc-datatype-valid.1.2.1", arguments);

        Assertions.assertThat(exception.getKey()).isEqualTo("cvc-datatype-valid.1.2.1");
        Assertions.assertThat(exception.getArgs()).containsExactly(arguments);
        Assertions.assertThat(exception.getMessage())
                .isEqualTo("cvc-datatype-valid.1.2.1: 'not-a-number' is not a valid 'integer' value.");
    }
}
