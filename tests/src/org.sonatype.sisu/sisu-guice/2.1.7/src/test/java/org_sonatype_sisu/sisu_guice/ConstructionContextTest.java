/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.junit.jupiter.api.Test;

public class ConstructionContextTest {
    @Test
    void resolvesCircularConstructorDependencyThroughInterfaceProxy() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(FirstService.class).to(FirstServiceImpl.class);
                bind(SecondService.class).to(SecondServiceImpl.class);
            }
        });

        FirstService service = injector.getInstance(FirstService.class);

        assertThat(service.name()).isEqualTo("first");
        assertThat(service.secondName()).isEqualTo("second");
        assertThat(service.nameSeenBySecond()).isEqualTo("first");
    }

    public interface FirstService {
        String name();

        String secondName();

        String nameSeenBySecond();
    }

    public interface SecondService {
        String name();

        String firstName();
    }

    public static class FirstServiceImpl implements FirstService {
        private final SecondService secondService;

        @Inject
        FirstServiceImpl(SecondService secondService) {
            this.secondService = secondService;
        }

        @Override
        public String name() {
            return "first";
        }

        @Override
        public String secondName() {
            return secondService.name();
        }

        @Override
        public String nameSeenBySecond() {
            return secondService.firstName();
        }
    }

    public static class SecondServiceImpl implements SecondService {
        private final FirstService firstService;

        @Inject
        SecondServiceImpl(FirstService firstService) {
            this.firstService = firstService;
        }

        @Override
        public String name() {
            return "second";
        }

        @Override
        public String firstName() {
            return firstService.name();
        }
    }
}
