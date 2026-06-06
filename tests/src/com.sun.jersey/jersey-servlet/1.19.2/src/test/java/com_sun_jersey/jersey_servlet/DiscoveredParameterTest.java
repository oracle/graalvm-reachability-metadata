/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_servlet;

import com.sun.jersey.server.impl.cdi.DiscoveredParameter;
import java.lang.annotation.Annotation;
import javax.ws.rs.QueryParam;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DiscoveredParameterTest {
    @Test
    void getValueReadsValueFromJaxRsAnnotation() {
        QueryParam annotation = new QueryParamAnnotation("customerId");
        DiscoveredParameter parameter = new DiscoveredParameter(annotation, String.class, null, false);

        assertThat(parameter.getValue()).isEqualTo("customerId");
        assertThat(parameter).extracting(DiscoveredParameter::getAnnotation).isSameAs(annotation);
        assertThat(parameter.getType()).isEqualTo(String.class);
        assertThat(parameter.getDefaultValue()).isNull();
        assertThat(parameter.isEncoded()).isFalse();
    }

    private static final class QueryParamAnnotation implements QueryParam {
        private final String value;

        private QueryParamAnnotation(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return QueryParam.class;
        }
    }
}
