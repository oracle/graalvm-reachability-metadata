/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_crac.crac;

import static javax.crac.Core.checkpointRestoreCount;
import static javax.crac.Core.clear;
import static javax.crac.Core.registeredResourceCount;
import static org.assertj.core.api.Assertions.assertThat;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CoreInnerCompatTest {
    @BeforeEach
    void clearCompatState() {
        clear();
    }

    @Test
    void registerAndCheckpointRestoreDelegateToCompatibilityApi() throws Exception {
        Core.getGlobalContext().register(new RecordingResource());

        assertThat(registeredResourceCount()).isEqualTo(1);

        Core.checkpointRestore();

        assertThat(checkpointRestoreCount()).isEqualTo(1);
    }

    private static final class RecordingResource implements Resource {
        @Override
        public void beforeCheckpoint(Context<? extends Resource> context) {
            throw new AssertionError("Unexpected checkpoint callback");
        }

        @Override
        public void afterRestore(Context<? extends Resource> context) {
            throw new AssertionError("Unexpected restore callback");
        }
    }
}
