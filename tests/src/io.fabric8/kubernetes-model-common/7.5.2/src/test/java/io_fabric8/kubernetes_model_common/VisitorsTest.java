/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_fabric8.kubernetes_model_common;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class VisitorsTest {
    @Test
    void typedVisitorResolvesParameterizedArrayType() {
        ListArrayVisitor visitor = new ListArrayVisitor();
        @SuppressWarnings("unchecked")
        List<String>[] target = new List[] {List.of("resource")};

        assertThat(visitor.getType()).isEqualTo(List[].class);
        assertThat(visitor.canVisit(Collections.emptyList(), target)).isTrue();

        visitor.visit(target);

        assertThat(visitor.getVisited()).containsExactly(List.of("resource"));
    }

    private static final class ListArrayVisitor extends TypedVisitor<List<String>[]> {
        private final AtomicReference<List<String>[]> visited = new AtomicReference<>();

        @Override
        public void visit(List<String>[] element) {
            visited.set(element);
        }

        List<String>[] getVisited() {
            return visited.get();
        }
    }
}
