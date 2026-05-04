/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api.gax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.gax.core.GaxProperties;
import com.google.api.gax.nativeimage.NativeImageUtils;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class NativeImageUtilsTest {
    @Test
    void getMethodOrFailReturnsDeclaredMethod() {
        Method method =
                NativeImageUtils.getMethodOrFail(GaxProperties.class, "getGaxVersion");

        assertThat(method.getDeclaringClass()).isEqualTo(GaxProperties.class);
        assertThat(method.getName()).isEqualTo("getGaxVersion");
        assertThat(method.getReturnType()).isEqualTo(String.class);
        assertThat(method.getParameterCount()).isZero();
    }

    @Test
    void getMethodOrFailThrowsForMissingMethod() {
        assertThatThrownBy(
                        () ->
                                NativeImageUtils.getMethodOrFail(
                                        GaxProperties.class, "missingGaxMethod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missingGaxMethod")
                .hasMessageContaining(GaxProperties.class.getName());
    }
}
