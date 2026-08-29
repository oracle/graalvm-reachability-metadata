/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.runtime.Resources;
import org.apache.calcite.util.Static;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourcesInnerInstTest {
    @Test
    void loadsCalciteMessageBundle() {
        Resources.Inst message = Static.RESOURCE.badFormat("timestamp");

        assertThat(message.bundle().getString("BadFormat")).contains("{0}");
        assertThat(message.str()).contains("timestamp");
    }
}
