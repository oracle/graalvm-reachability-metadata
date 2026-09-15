/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.sshd.common.kex.DHGroupData;
import org.junit.jupiter.api.Test;

public class DHGroupDataTest {
    @Test
    void loadsOakleyGroupPrimeFromLibraryResource() {
        byte[] prime = DHGroupData.getP15();

        assertThat(prime).isNotEmpty();
        assertThat(prime[0]).isZero();
    }
}
