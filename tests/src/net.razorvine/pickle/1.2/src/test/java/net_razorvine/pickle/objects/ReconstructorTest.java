/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_razorvine.pickle.objects;

import net.razorvine.pickle.objects.Reconstructor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReconstructorTest {
    @Test
    void invokesReconstructMethodWithStateArguments() {
        Object value = new Reconstructor().construct(new Object[]{new ReconstructionTarget(), "value", Integer.valueOf(3)});

        assertThat(value).isEqualTo("value:3");
    }

    public static final class ReconstructionTarget {
        public String reconstruct(Object first, Object second) {
            return first + ":" + second;
        }
    }
}
