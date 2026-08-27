/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.modelmapper.ExpressionMap;
import org.modelmapper.ModelMapper;
import org.modelmapper.builder.ConfigurableConditionExpression;
import org.modelmapper.spi.DestinationSetter;
import org.modelmapper.spi.SourceGetter;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaDispatcherTest {
    @Test
    void appliesExplicitPropertyMapping() {
        try {
            ModelMapper modelMapper = new ModelMapper();
            modelMapper.typeMap(Source.class, Destination.class)
                    .addMappings(new NameExpressionMap());

            Destination destination = modelMapper.map(new Source("Ada"), Destination.class);

            assertThat(destination.getDisplayName()).isEqualTo("Ada");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class NameExpressionMap implements ExpressionMap<Source, Destination> {
        @Override
        public void configure(ConfigurableConditionExpression<Source, Destination> mapping) {
            mapping.map(new SourceNameGetter(), new DestinationNameSetter());
        }
    }

    public static class SourceNameGetter implements SourceGetter<Source> {
        @Override
        public Object get(Source source) {
            return source.getName();
        }
    }

    public static class DestinationNameSetter implements DestinationSetter<Destination, Object> {
        @Override
        public void accept(Destination destination, Object value) {
            destination.setDisplayName((String) value);
        }
    }

    public static class Source {
        private final String name;

        public Source(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class Destination {
        private String displayName;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }
}
