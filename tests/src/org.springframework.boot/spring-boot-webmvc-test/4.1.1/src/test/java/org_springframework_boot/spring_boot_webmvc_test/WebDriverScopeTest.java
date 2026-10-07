/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_webmvc_test;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.webmvc.test.autoconfigure.WebDriverScope;

import static org.assertj.core.api.Assertions.assertThat;

public class WebDriverScopeTest {

    @Test
    void storesOneInstancePerNameUntilItIsRemoved() {
        WebDriverScope scope = new WebDriverScope();
        AtomicInteger sequence = new AtomicInteger();
        ResourceFactory factory = new ResourceFactory(sequence);

        ScopedResource first = (ScopedResource) scope.get("browser", factory);
        ScopedResource cached = (ScopedResource) scope.get("browser", factory);
        ScopedResource other = (ScopedResource) scope.get("other", factory);

        assertThat(cached).isSameAs(first);
        assertThat(other.id()).isEqualTo(2);
        assertThat(scope.remove("browser")).isSameAs(first);

        ScopedResource replacement = (ScopedResource) scope.get("browser", factory);
        assertThat(replacement).isNotSameAs(first);
        assertThat(replacement.id()).isEqualTo(3);
        assertThat(scope.remove("missing")).isNull();
    }

    private static final class ResourceFactory implements ObjectFactory<ScopedResource> {

        private final AtomicInteger sequence;

        private ResourceFactory(AtomicInteger sequence) {
            this.sequence = sequence;
        }

        @Override
        public ScopedResource getObject() {
            return new ScopedResource(this.sequence.incrementAndGet());
        }
    }

    private record ScopedResource(int id) {
    }
}
