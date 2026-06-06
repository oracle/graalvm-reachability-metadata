/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.builder.PathAwareTypedVisitor;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class PathAwareTypedVisitorTest {
    @Test
    void detectsPathAwareVisitMethodForMatchingElementType() {
        RecordingVisitor visitor = new RecordingVisitor();
        ChildResource child = new ChildResource("child");
        ParentResource parent = new ParentResource("parent");

        assertThat(visitor.hasVisitMethodMatching(child)).isTrue();

        visitor.visit(List.of(), child);

        assertThat(visitor.getType()).isEqualTo(ChildResource.class);
        assertThat(visitor.getParentType()).isEqualTo(ParentResource.class);
        assertThat(visitor.getParent(List.of(parent))).isSameAs(parent);
        assertThat(visitor.visited()).isSameAs(child);
    }

    public static final class RecordingVisitor extends PathAwareTypedVisitor<ChildResource, ParentResource> {
        private final AtomicReference<ChildResource> visited = new AtomicReference<>();

        @Override
        public void visit(List<Entry<String, Object>> path, ChildResource element) {
            visited.set(element);
        }

        ChildResource visited() {
            return visited.get();
        }
    }

    public static final class ChildResource {
        private final String name;

        ChildResource(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static final class ParentResource {
        private final String name;

        ParentResource(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
