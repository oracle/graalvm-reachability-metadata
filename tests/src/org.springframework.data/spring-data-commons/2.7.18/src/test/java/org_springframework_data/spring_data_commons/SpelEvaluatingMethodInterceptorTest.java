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
    void evaluatesInheritedExpressionUsingTargetAndMethodArguments() {
        SpelAwareProxyProjectionFactory factory = new SpelAwareProxyProjectionFactory();
        Greeting projection = factory.createProjection(Greeting.class,
                Map.of("firstname", "Ada", "lastname", "Lovelace"));

        assertThat(projection.getFullName()).isEqualTo("Ada Lovelace");
        assertThat(projection.greet("Hello")).isEqualTo("Hello, Ada");
        assertThat(projection.getFirstname()).isEqualTo("Ada");
    }

    public interface FullName {

        @Value("#{target.firstname + ' ' + target.lastname}")
        String getFullName();
    }

    public interface Greeting extends FullName {

        String getFirstname();

        @Value("#{args[0] + ', ' + target.firstname}")
        String greet(String prefix);
    }
}
