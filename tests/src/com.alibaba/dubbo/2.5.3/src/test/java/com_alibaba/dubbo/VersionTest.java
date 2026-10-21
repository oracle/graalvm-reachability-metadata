/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.Version;
import org.junit.jupiter.api.Test;

public class VersionTest {
    @Test
    void readsItsVersionAndChecksClasspathDuplicates() {
        assertThat(Version.getVersion()).isNotBlank();
        assertThat(Version.isInternalVersion()).isFalse();

        Version.checkDuplicate(Version.class, true);
    }
}
