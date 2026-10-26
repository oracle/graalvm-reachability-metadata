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

public class ParamConvertersInnerTypeFromStringAnonymous1Test {
    @Test
    void convertsValueWithStaticFromStringMethod() {
        ParamConverters.AggregatedProvider provider =
                new ParamConverters.AggregatedProvider(null, new ClientConfig());
        ParamConverter<FromStringValue> converter = provider.getConverter(
                FromStringValue.class, FromStringValue.class, new Annotation[0]);

        FromStringValue value = converter.fromString("partitioned");

        assertThat(value.text).isEqualTo("from:partitioned");
    }

    public static class FromStringValue {
        private final String text;

        private FromStringValue(String text) {
            this.text = text;
        }

        public static FromStringValue fromString(String text) {
            return new FromStringValue("from:" + text);
        }
    }
}
