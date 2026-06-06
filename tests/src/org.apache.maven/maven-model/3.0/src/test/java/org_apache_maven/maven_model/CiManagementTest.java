/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model;

import org.apache.maven.model.CiManagement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CiManagementTest {
    @Test
    void addsNullNotifierToNotifiers() {
        CiManagement ciManagement = new CiManagement();

        ciManagement.addNotifier(null);

        assertThat(ciManagement.getNotifiers()).hasSize(1);
        assertThat(ciManagement.getNotifiers().get(0)).isNull();
    }
}
