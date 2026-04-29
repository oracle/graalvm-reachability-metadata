/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration.AccessLevel;

public class PropertyInfoImplInnerFieldPropertyInfoTest {
    @Test
    void mapsValuesThroughPrivateFieldAccessorsAndMutators() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
            .setFieldMatchingEnabled(true)
            .setFieldAccessLevel(AccessLevel.PRIVATE);

        FieldBackedSource source = new FieldBackedSource("Kestrel", new SourceAddress("Prague"), 7);

        FieldBackedDestination destination = modelMapper.map(source, FieldBackedDestination.class);

        assertThat(destination.summary()).isEqualTo("Kestrel in Prague has priority 7");
    }

    public static final class FieldBackedSource {
        private final String name;
        private final SourceAddress address;
        private final int priority;

        public FieldBackedSource(String name, SourceAddress address, int priority) {
            this.name = name;
            this.address = address;
            this.priority = priority;
        }
    }

    public static final class SourceAddress {
        private final String city;

        public SourceAddress(String city) {
            this.city = city;
        }
    }

    public static final class FieldBackedDestination {
        private String name;
        private DestinationAddress address;
        private int priority;

        public FieldBackedDestination() {
        }

        public String summary() {
            return name + " in " + address.city() + " has priority " + priority;
        }
    }

    public static final class DestinationAddress {
        private String city;

        public DestinationAddress() {
        }

        public String city() {
            return city;
        }
    }
}
