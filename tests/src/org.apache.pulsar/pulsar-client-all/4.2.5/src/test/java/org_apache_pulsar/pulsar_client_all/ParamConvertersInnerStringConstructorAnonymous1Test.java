/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import org.apache.pulsar.shade.javax.ws.rs.ext.ParamConverter;
import org.apache.pulsar.shade.org.glassfish.jersey.client.ClientConfig;
import org.apache.pulsar.shade.org.glassfish.jersey.internal.inject.ParamConverters;
import org.junit.jupiter.api.Test;

public class ParamConvertersInnerStringConstructorAnonymous1Test {
    @Test
    void convertsValueWithStringConstructor() {
        ParamConverters.AggregatedProvider provider =
                new ParamConverters.AggregatedProvider(null, new ClientConfig());
        ParamConverter<ConstructedValue> converter = provider.getConverter(
                ConstructedValue.class, ConstructedValue.class, new Annotation[0]);

        ConstructedValue value = converter.fromString("persistent");

        assertThat(value.text).isEqualTo("persistent");
    }

    public static class ConstructedValue {
        private final String text;

        public ConstructedValue(String text) {
            this.text = text;
        }
    }
}
