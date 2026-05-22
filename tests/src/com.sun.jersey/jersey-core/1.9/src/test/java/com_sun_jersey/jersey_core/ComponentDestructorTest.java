/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.spi.component.ComponentDestructor;
import javax.annotation.PreDestroy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ComponentDestructorTest {
    @Test
    public void invokesPreDestroyLifecycleMethod() throws Exception {
        final ComponentDestructor componentDestructor = new ComponentDestructor(ManagedComponent.class);
        final ManagedComponent component = new ManagedComponent();

        componentDestructor.destroy(component);

        assertThat(component.isDestroyed()).isTrue();
    }

    public static class ManagedComponent {
        private boolean destroyed;

        @PreDestroy
        public void destroy() {
            destroyed = true;
        }

        public boolean isDestroyed() {
            return destroyed;
        }
    }
}
