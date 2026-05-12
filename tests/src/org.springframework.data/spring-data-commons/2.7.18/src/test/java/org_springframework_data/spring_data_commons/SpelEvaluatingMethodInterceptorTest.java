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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

public class SpelEvaluatingMethodInterceptorTest {

    @Test
    void evaluatesValueAnnotatedMethodOnProjectionInterface() {
        SpelAwareProxyProjectionFactory factory = new SpelAwareProxyProjectionFactory();
        Map<String, Object> source = Map.of(
                "firstName", "Ada",
                "lastName", "Lovelace");

        NameProjection projection = factory.createProjection(NameProjection.class, source);

        assertThat(projection.getFirstName()).isEqualTo("Ada");
        assertThat(projection.getFullName()).isEqualTo("Ada Lovelace");
    }

    public interface NameProjection {

        String getFirstName();

        @Value("#{target['firstName'] + ' ' + target['lastName']}")
        String getFullName();
    }
}
