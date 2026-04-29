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
import org.modelmapper.PropertyMap;

public class MembersTest {
    @Test
    void explicitFieldMappingMapsSourceFieldToDestinationField() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.addMappings(new PropertyMap<FieldSource, FieldDestination>() {
            @Override
            protected void configure() {
                map(source.name, destination.displayName);
            }
        });

        FieldSource source = new FieldSource();
        source.name = "Grace Hopper";

        FieldDestination destination = modelMapper.map(source, FieldDestination.class);

        assertThat(destination.displayName).isEqualTo("Grace Hopper");
    }

    public static class FieldSource {
        public String name;
    }

    public static class FieldDestination {
        public String displayName;
    }
}
