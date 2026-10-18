/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import org.apache.pulsar.shade.io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

public class PlatformDependent0Test {

    @Test
    void alignsDirectBuffersAndRecognizesPlatformThreads() {
        final ByteBuffer direct = ByteBuffer.allocateDirect(128);

        final ByteBuffer aligned = PlatformDependent.alignDirectBuffer(direct, 16);

        assertThat(aligned.isDirect()).isTrue();
        assertThat(aligned.capacity()).isGreaterThanOrEqualTo(112);
        assertThat(PlatformDependent.isVirtualThread(Thread.currentThread())).isFalse();
    }
}
