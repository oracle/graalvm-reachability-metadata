/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_razorvine.pickle;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UnpicklerTest {
    private static final byte[] BUILD_PICKLE = "ctest\nBuildable\n(tRS'configured'\nb."
            .getBytes(StandardCharsets.US_ASCII);

    @Test
    void appliesSetStateDuringBuildOpcode() throws Exception {
        Unpickler.registerConstructor("test", "Buildable", new BuildableConstructor());

        Object value = new Unpickler().loads(BUILD_PICKLE);

        assertThat(value).isInstanceOfSatisfying(BuildableObject.class,
                buildable -> assertThat(buildable.state()).isEqualTo("configured"));
    }

    private static final class BuildableConstructor implements IObjectConstructor {
        @Override
        public Object construct(Object[] args) {
            return new BuildableObject();
        }
    }

    public static final class BuildableObject {
        private String state;

        public void __setstate__(String state) {
            this.state = state;
        }

        public String state() {
            return state;
        }
    }
}
