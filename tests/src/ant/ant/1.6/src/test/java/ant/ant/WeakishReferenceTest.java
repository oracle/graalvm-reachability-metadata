/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.ref.Reference;

import org.apache.tools.ant.util.WeakishReference;
import org.junit.jupiter.api.Test;

public class WeakishReferenceTest {
    @Test
    void retrievesLiveReferentFromVersionCompatibleReference() {
        Object value = new Object();
        WeakishReference reference = WeakishReference.createReference(value);
        assertSame(value, reference.get());
        Reference.reachabilityFence(value);
    }
}
