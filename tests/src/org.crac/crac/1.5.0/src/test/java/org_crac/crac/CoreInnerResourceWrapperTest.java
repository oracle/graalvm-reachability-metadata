/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_crac.crac;

import static javax.crac.Core.clear;
import static javax.crac.Core.firstRegisteredResourceEquals;
import static org.assertj.core.api.Assertions.assertThat;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CoreInnerResourceWrapperTest {
    @BeforeEach
    void clearCompatState() {
        clear();
    }

    @Test
    void objectMethodCallsAreDelegatedToWrappedResource() {
        Resource resource = new PassiveResource();

        Core.getGlobalContext().register(resource);

        assertThat(firstRegisteredResourceEquals(resource)).isTrue();
    }

    private static final class PassiveResource implements Resource {
        @Override
        public void beforeCheckpoint(Context<? extends Resource> context) {
        }

        @Override
        public void afterRestore(Context<? extends Resource> context) {
        }
    }
}
