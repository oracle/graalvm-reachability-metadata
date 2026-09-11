/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

public class DefaultMethodInvokingMethodInterceptorInnerMethodHandleLookupTest {

    @Test
    void projectionInvokesInterfaceDefaultMethod() {
        ProjectionFactory factory = new SpelAwareProxyProjectionFactory();
        Map<String, Object> source = new HashMap<>();
        source.put("firstname", "Ada");
        source.put("lastname", "Lovelace");

        FullNameProjection projection = factory.createProjection(FullNameProjection.class, source);

        assertThat(projection.getFullName()).isEqualTo("Ada Lovelace");
    }

    public interface FullNameProjection {

        String getFirstname();

        String getLastname();

        default String getFullName() {
            return getFirstname() + " " + getLastname();
        }
    }
}
