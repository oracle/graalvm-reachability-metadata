/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.utility.JavaConstant;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaConstantInnerMethodHandleInnerDispatcherInnerAbstractBaseTest {
    @Test
    void describesLoadedMethodHandleAndLookupType() throws Exception {
        java.lang.invoke.MethodHandle loadedHandle = MethodHandles.publicLookup().findVirtual(
                String.class,
                "substring",
                MethodType.methodType(String.class, int.class, int.class));

        JavaConstant.MethodHandle methodHandle = JavaConstant.MethodHandle.ofLoaded(loadedHandle);

        assertThat(methodHandle.getHandleType()).isEqualTo(JavaConstant.MethodHandle.HandleType.INVOKE_VIRTUAL);
        assertThat(methodHandle.getOwnerType().represents(String.class)).isTrue();
        assertThat(methodHandle.getName()).isEqualTo("substring");
        assertThat(methodHandle.getReturnType().represents(String.class)).isTrue();
        assertThat(methodHandle.getDescriptor()).isEqualTo("(II)Ljava/lang/String;");
        assertThat(JavaConstant.MethodHandle.lookupType(MethodHandles.lookup()))
                .isEqualTo(JavaConstantInnerMethodHandleInnerDispatcherInnerAbstractBaseTest.class);
    }
}
