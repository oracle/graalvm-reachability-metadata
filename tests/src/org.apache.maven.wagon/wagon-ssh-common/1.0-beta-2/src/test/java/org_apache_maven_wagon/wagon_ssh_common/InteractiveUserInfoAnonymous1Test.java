/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_wagon.wagon_ssh_common;

import org.apache.maven.wagon.providers.ssh.interactive.InteractiveUserInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InteractiveUserInfoAnonymous1Test {
    @Test
    void roleUsesInteractiveUserInfoClassName() {
        assertThat(InteractiveUserInfo.ROLE)
                .isEqualTo(InteractiveUserInfo.class.getName());
    }
}
