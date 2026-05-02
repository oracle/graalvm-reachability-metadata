/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.util.WeakishReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WeakishReferenceTest {
    private static final String JAVA_12_REFERENCE_CLASS_NAME =
            "org.apache.tools.ant.util.optional.WeakishReference12";

    @Test
    void createsWeakReferenceUsingPublicFactory() {
        Object referent = new Object();

        WeakishReference reference = WeakishReference.createReference(referent);

        assertThat(reference.get()).isSameAs(referent);
        assertThat(reference.getClass().getName()).isEqualTo(JAVA_12_REFERENCE_CLASS_NAME);
    }
}
