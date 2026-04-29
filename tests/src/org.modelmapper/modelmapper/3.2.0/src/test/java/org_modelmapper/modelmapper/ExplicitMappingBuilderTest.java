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

public class ExplicitMappingBuilderTest {
    @Test
    void propertyMapUsesExplicitBuilderToConfigureMappings() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.addMappings(new PropertyMap<SourcePerson, DestinationPerson>() {
            @Override
            protected void configure() {
                map().setDisplayName(source.getName());
                skip().setIgnored(null);
            }
        });

        SourcePerson source = new SourcePerson("Ada Lovelace", "internal-id");
        DestinationPerson destination = modelMapper.map(source, DestinationPerson.class);

        assertThat(destination.getDisplayName()).isEqualTo("Ada Lovelace");
        assertThat(destination.getIgnored()).isNull();
    }

    public static class SourcePerson {
        private final String name;
        private final String ignored;

        public SourcePerson(String name, String ignored) {
            this.name = name;
            this.ignored = ignored;
        }

        public String getName() {
            return name;
        }

        public String getIgnored() {
            return ignored;
        }
    }

    public static class DestinationPerson {
        private String displayName;
        private String ignored;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getIgnored() {
            return ignored;
        }

        public void setIgnored(String ignored) {
            this.ignored = ignored;
        }
    }
}
