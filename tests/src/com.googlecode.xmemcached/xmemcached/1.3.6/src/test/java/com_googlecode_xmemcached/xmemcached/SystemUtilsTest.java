/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_googlecode_xmemcached.xmemcached;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.code.yanf4j.util.SystemUtils;
import java.nio.channels.Selector;
import org.junit.jupiter.api.Test;

public class SystemUtilsTest {
    @Test
    void opensAUsablePlatformSelector() throws Exception {
        try (Selector selector = SystemUtils.openSelector()) {
            assertThat(selector.isOpen()).isTrue();
            assertThat(selector.selectNow()).isZero();
        }
    }
}
