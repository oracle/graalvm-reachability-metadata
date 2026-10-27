/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.util.ReferenceManager;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class ReferenceManagerImplTest {
    @Test
    void keepsCleanupPendingWhileTheRegisteredObjectIsReachable() {
        Object referenced = new Object();
        AtomicBoolean cleaned = new AtomicBoolean();

        ReferenceManager.INSTANCE.register(referenced, () -> cleaned.set(true));

        assertThat(referenced).isNotNull();
        assertThat(cleaned).isFalse();
    }
}
