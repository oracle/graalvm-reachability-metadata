/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package biz_aQute_bnd.biz_aQute_bnd_annotation;

import static org.assertj.core.api.Assertions.assertThat;

import aQute.bnd.annotation.metatype.Configurable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

public class ConfigurableInnerConfigurableHandlerTest {
    @Test
    void convertsClassValuesArraysMapsAndBndAnnotationValues() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("plainClass", "java.lang.String");
        properties.put("genericClass", "java.lang.String");
        properties.put("numbers", "1|2|3");
        properties.put("integerMap", Map.of("one", "1", "two", "2"));

        aQute.bnd.osgi.Annotation markerAnnotation = new aQute.bnd.osgi.Annotation(new MarkerImpl("configured"));
        assertThat(markerAnnotation.
            getAnnotation()).isInstanceOf(Marker.class);
        properties.put("marker", markerAnnotation);

        ConversionConfiguration configuration = Configurable.createConfigurable(ConversionConfiguration.class,
            properties);

        assertThat(configuration.plainClass()).isSameAs(String.class);
        assertThat(configuration.genericClass()).isSameAs(String.class);
        assertThat(configuration.numbers()).containsExactly(1, 2, 3);
        assertThat(configuration.integerMap()).containsEntry("one", 1)
            .containsEntry("two", 2);
        assertThat(configuration.marker()
            .value()).isEqualTo("configured");
    }

    public interface ConversionConfiguration {
        @SuppressWarnings("rawtypes")
        Class plainClass();

        Class<?> genericClass();

        Integer[] numbers();

        TreeMap<String, Integer> integerMap();

        Marker marker();
    }

    public @interface Marker {
        String value();
    }

    private static final class MarkerImpl implements Marker {
        private final String value;

        private MarkerImpl(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return Marker.class;
        }
    }
}
