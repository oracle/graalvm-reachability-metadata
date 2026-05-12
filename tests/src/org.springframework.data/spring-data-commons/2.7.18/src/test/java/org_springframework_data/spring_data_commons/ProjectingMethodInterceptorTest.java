/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

public class ProjectingMethodInterceptorTest {

    @Test
    void projectsCollectionElementsIntoProjectionArray() {
        SpelAwareProxyProjectionFactory factory = new SpelAwareProxyProjectionFactory();
        Map<String, Object> source = Map.of("items", List.of(
                Map.of("name", "first"),
                Map.of("name", "second")));

        ContainerProjection projection = factory.createProjection(ContainerProjection.class, source);

        ItemProjection[] items = projection.getItems();
        assertThat(items).hasSize(2);
        assertThat(items).extracting(ItemProjection::getName).containsExactly("first", "second");
    }

    public interface ContainerProjection {

        ItemProjection[] getItems();
    }

    public interface ItemProjection {

        String getName();
    }
}
