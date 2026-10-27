/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.internal.VirtualThreadUtils;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class VirtualThreadUtilsTest {

    @Test
    void createsVirtualThreadExecutorAndIdentifiesVirtualThreads() throws Exception {
        assertThat(VirtualThreadUtils.isVirtualThreadsSupported()).isTrue();
        assertThat(VirtualThreadUtils.isVirtualThread()).isFalse();

        try (ExecutorService executor = VirtualThreadUtils.createVirtualThreadExecutor()) {
            boolean virtual = executor.submit(VirtualThreadUtils::isVirtualThread).get(30, TimeUnit.SECONDS);

            assertThat(virtual).isTrue();
        }
    }
}
