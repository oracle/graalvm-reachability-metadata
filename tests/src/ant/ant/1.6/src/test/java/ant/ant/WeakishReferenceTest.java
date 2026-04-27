/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.util.WeakishReference;
import org.junit.jupiter.api.Test;

public class WeakishReferenceTest {
    @Test
    void createsReferenceUsingRuntimeSpecificImplementation() {
        Object referent = new Object();

        WeakishReference reference = WeakishReference.createReference(referent);

        assertThat(reference).isNotNull();
        assertThat(reference.get()).isSameAs(referent);
    }
}
