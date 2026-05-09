/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.core.spi.component.ComponentDestructor;
import javax.annotation.PreDestroy;
import org.junit.jupiter.api.Test;

public class ComponentDestructorTest {
    @Test
    void invokesPreDestroyMethodWhenDestroyingComponent() throws Exception {
        final ComponentDestructor destructor = new ComponentDestructor(PreDestroyComponent.class);
        final PreDestroyComponent component = new PreDestroyComponent();

        destructor.destroy(component);

        assertThat(component.destroyed).isTrue();
    }

    public static class PreDestroyComponent {
        private boolean destroyed;

        @PreDestroy
        public void destroy() {
            this.destroyed = true;
        }
    }
}
