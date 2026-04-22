/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opencensus.opencensus_api;

import static org.assertj.core.api.Assertions.assertThat;

import io.opencensus.internal.Provider;
import org.junit.jupiter.api.Test;

public class ProviderTest {

    @Test
    void createInstanceUsesPublicNoArgsConstructor() {
        TestServiceImpl.reset();

        TestService service = Provider.createInstance(TestServiceImpl.class, TestService.class);

        assertThat(service).isInstanceOf(TestServiceImpl.class);
        assertThat(service.describe()).isEqualTo("created");
        assertThat(TestServiceImpl.instantiationCount).isEqualTo(1);
    }

    interface TestService {

        String describe();
    }

    public static final class TestServiceImpl implements TestService {

        private static int instantiationCount;

        public TestServiceImpl() {
            instantiationCount++;
        }

        static void reset() {
            instantiationCount = 0;
        }

        @Override
        public String describe() {
            return "created";
        }
    }
}
