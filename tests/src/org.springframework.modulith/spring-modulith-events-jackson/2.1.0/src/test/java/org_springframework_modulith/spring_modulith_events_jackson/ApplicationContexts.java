/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_events_jackson;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class ApplicationContexts {
    private ApplicationContexts() {
    }

    public static ConfigurableApplicationContext start(Class<?> applicationClass) {
        return new AnnotationConfigApplicationContext(applicationClass);
    }
}
