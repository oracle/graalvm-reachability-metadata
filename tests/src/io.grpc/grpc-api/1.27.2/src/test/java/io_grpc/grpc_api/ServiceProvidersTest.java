/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.InternalServiceProviders;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ServiceProvidersTest {
    @Test
    void getCandidatesViaHardCodedInstantiatesProviderWithPublicConstructor() {
        List<Class<?>> hardcodedProviders = Collections.singletonList(HardcodedProvider.class);

        Iterable<TestService> candidates = InternalServiceProviders.getCandidatesViaHardCoded(
                TestService.class,
                hardcodedProviders);

        List<TestService> providers = new ArrayList<>();
        for (TestService candidate : candidates) {
            providers.add(candidate);
        }

        assertThat(providers).hasSize(1);
        assertThat(providers.get(0).name()).isEqualTo("hard-coded-provider");
    }

    public interface TestService {
        String name();
    }

    public static final class HardcodedProvider implements TestService {
        public HardcodedProvider() {
        }

        @Override
        public String name() {
            return "hard-coded-provider";
        }
    }
}
