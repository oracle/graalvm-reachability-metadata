/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_config;

import org.springframework.security.config.annotation.ObjectPostProcessor;

final class SecurityTestSupport {
    private SecurityTestSupport() {
    }

    static ObjectPostProcessor<Object> objectPostProcessor() {
        return new ObjectPostProcessor<>() {
            @Override
            public <O> O postProcess(O object) {
                return object;
            }
        };
    }
}
