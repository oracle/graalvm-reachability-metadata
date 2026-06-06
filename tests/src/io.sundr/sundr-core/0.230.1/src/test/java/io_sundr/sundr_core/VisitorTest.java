/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.builder.Visitor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class VisitorTest {
    @Test
    void detectsSingleArgumentVisitMethodForMatchingElementType() {
        RecordingVisitor visitor = new RecordingVisitor();
        VisitedResource resource = new VisitedResource("visited");

        assertThat(visitor.hasVisitMethodMatching(resource)).isTrue();

        visitor.visit(resource);

        assertThat(visitor.visited()).isSameAs(resource);
        assertThat(visitor.visited().getName()).isEqualTo("visited");
    }

    public static final class RecordingVisitor implements Visitor<VisitedResource> {
        private final AtomicReference<VisitedResource> visited = new AtomicReference<>();

        @Override
        public void visit(VisitedResource element) {
            visited.set(element);
        }

        VisitedResource visited() {
            return visited.get();
        }
    }

    public static final class VisitedResource {
        private final String name;

        VisitedResource(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
