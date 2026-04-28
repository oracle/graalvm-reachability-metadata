/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.util.BitSet;

import com.thoughtworks.xstream.core.util.DependencyInjectionFactory;
import com.thoughtworks.xstream.core.util.TypedNull;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DependencyInjectionFactoryTest {
    @Test
    void createsInstanceWithTypedNullAndMatchingDependencies() {
        Collaborator collaborator = new Collaborator("native-image");
        BitSet usedDependencies = new BitSet();

        Object instance = DependencyInjectionFactory.newInstance(
                InjectableService.class,
                new Object[]{new TypedNull(CharSequence.class), Integer.valueOf(20), collaborator},
                usedDependencies);

        assertThat(instance).isInstanceOf(InjectableService.class);
        InjectableService service = (InjectableService)instance;
        assertThat(service.name).isNull();
        assertThat(service.count).isEqualTo(20);
        assertThat(service.collaborator).isSameAs(collaborator);
        assertThat(usedDependencies.get(0)).isTrue();
        assertThat(usedDependencies.get(1)).isTrue();
        assertThat(usedDependencies.get(2)).isTrue();
        assertThat(usedDependencies.get(3)).isFalse();
    }

    public static final class InjectableService {
        private final CharSequence name;
        private final Number count;
        private final Collaborator collaborator;

        public InjectableService() {
            this("default", Integer.valueOf(0), new Collaborator("default"));
        }

        public InjectableService(CharSequence name) {
            this(name, Integer.valueOf(0), new Collaborator("named"));
        }

        public InjectableService(CharSequence name, Number count, Collaborator collaborator) {
            this.name = name;
            this.count = count;
            this.collaborator = collaborator;
        }
    }

    public static final class Collaborator {
        private final String value;

        Collaborator(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
