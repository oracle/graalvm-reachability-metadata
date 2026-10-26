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

public class ParamConvertersInnerTypeValueOfAnonymous1Test {
    @Test
    void convertsValueWithStaticValueOfMethod() {
        ParamConverters.AggregatedProvider provider =
                new ParamConverters.AggregatedProvider(null, new ClientConfig());
        ParamConverter<ValueOfValue> converter = provider.getConverter(
                ValueOfValue.class, ValueOfValue.class, new Annotation[0]);

        ValueOfValue value = converter.fromString("shared");

        assertThat(value.text).isEqualTo("value:shared");
    }

    public static class ValueOfValue {
        private final String text;

        private ValueOfValue(String text) {
            this.text = text;
        }

        public static ValueOfValue valueOf(String text) {
            return new ValueOfValue("value:" + text);
        }
    }
}
