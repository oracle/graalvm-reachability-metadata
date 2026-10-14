/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.flycapture;

import static org.assertj.core.api.Assertions.assertThat;

import org.bytedeco.flycapture.FlyCapture2_C.fc2Context;
import org.bytedeco.javacpp.BytePointer;
import org.junit.jupiter.api.Test;

public class Fc2ContextTest {
    @Test
    void convertsNativePointerToTypedContextHandle() {
        try (BytePointer nativeContext = new BytePointer(1);
                fc2Context context = nativeContext.getPointer(fc2Context.class)) {
            assertThat(context).isNotNull();
            assertThat(context.isNull()).isFalse();
            assertThat(context.address()).isEqualTo(nativeContext.address());
        }
    }
}
