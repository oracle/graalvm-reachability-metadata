/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v1.cachedstore.SoftSetFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class SoftSetFactoryTest {
    @Test
    @SuppressWarnings("unchecked")
    void createSynchronousCleanupSoftSetReturnsWorkingSetProxy() {
        Set<Object> set = (Set<Object>) SoftSetFactory.createSynchronousCleanupSoftSet();

        assertThat(set.add("alpha")).isTrue();
        assertThat(set.add("alpha")).isFalse();
        assertThat(set.contains("alpha")).isTrue();
        assertThat(set.size()).isEqualTo(1);
        assertThat(set.remove("alpha")).isTrue();
        assertThat(set.size()).isZero();
    }
}
