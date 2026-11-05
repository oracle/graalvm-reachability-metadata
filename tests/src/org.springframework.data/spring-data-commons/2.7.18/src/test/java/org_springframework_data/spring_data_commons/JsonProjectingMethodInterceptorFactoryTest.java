/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.web.JsonPath;
import org.springframework.data.web.JsonProjectingMethodInterceptorFactory;

public class JsonProjectingMethodInterceptorFactoryTest {

    @Test
    void annotatedMapProjectionReadsNestedAndFallbackJsonPaths() {
        JsonProjectingMethodInterceptorFactory interceptorFactory = new JsonProjectingMethodInterceptorFactory(
                new JacksonMappingProvider());
        SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
        projections.registerMethodInvokerFactory(interceptorFactory);
        Map<String, Object> document = Map.of("profile", Map.of("displayName", "Ada"), "enabled", true);

        Profile profile = projections.createProjection(Profile.class, document);

        assertThat(profile.getName()).isEqualTo("Ada");
        assertThat(profile.isEnabled()).isTrue();
        assertThat(interceptorFactory.supports(document, PlainProfile.class)).isFalse();
    }

    public interface Profile {

        @JsonPath({"$.profile.nickname", "$.profile.displayName"})
        String getName();

        boolean isEnabled();
    }

    public interface PlainProfile {

        String getName();
    }
}
