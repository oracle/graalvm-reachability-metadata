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
    void projectsCollectionElementsIntoTypedInterfaceArray() {
        SpelAwareProxyProjectionFactory factory = new SpelAwareProxyProjectionFactory();
        Map<String, Object> source = Map.of("members", List.of(Map.of("name", "Ada"), Map.of("name", "Grace")));

        Team projection = factory.createProjection(Team.class, source);
        Member[] members = projection.getMembers();

        assertThat(members).isInstanceOf(Member[].class).hasSize(2);
        assertThat(members).extracting(Member::getName).containsExactly("Ada", "Grace");
    }

    @Test
    void projectsEmptyCollectionIntoEmptyTypedArray() {
        SpelAwareProxyProjectionFactory factory = new SpelAwareProxyProjectionFactory();
        Team projection = factory.createProjection(Team.class, Map.of("members", List.of()));

        assertThat(projection.getMembers()).isInstanceOf(Member[].class).isEmpty();
    }

    public interface Team {

        Member[] getMembers();
    }

    public interface Member {

        String getName();
    }
}
