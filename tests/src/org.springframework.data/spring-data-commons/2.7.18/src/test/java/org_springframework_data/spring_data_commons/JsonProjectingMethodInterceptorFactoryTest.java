/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.web.JsonPath;
import org.springframework.data.web.JsonProjectingMethodInterceptorFactory;

import com.jayway.jsonpath.spi.mapper.JsonSmartMappingProvider;

public class JsonProjectingMethodInterceptorFactoryTest {

    @Test
    void detectsJsonPathAnnotatedProjectionMethodsForMapSources() {
        JsonProjectingMethodInterceptorFactory factory = new JsonProjectingMethodInterceptorFactory(
                new JsonSmartMappingProvider());

        assertThat(factory.supports(Map.of("name", "spring"), JsonPathProjection.class)).isTrue();
        assertThat(factory.supports(Map.of("name", "spring"), PlainProjection.class)).isFalse();
    }

    public interface JsonPathProjection {

        @JsonPath("$.name")
        String getName();
    }

    public interface PlainProjection {

        String getName();
    }
}
