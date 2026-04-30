/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_tcnative_classes;

import io.netty.internal.tcnative.Library;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LibraryTest {
    @Test
    void initializePreloadsNativeCallbackClasses() {
        assertThatThrownBy(() -> Library.initialize())
                .isInstanceOf(UnsatisfiedLinkError.class);
    }
}
