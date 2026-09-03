/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.runtime.CalciteResource;
import org.apache.calcite.runtime.Resources;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourcesTest {
    @Test
    void createsAndValidatesResourceProxy() {
        CalciteResource resource = Resources.create(CalciteResource.class);

        assertThat(resource.badFormat("date").str()).contains("date");
        Resources.validate(resource, EnumSet.of(
                Resources.Validation.AT_LEAST_ONE,
                Resources.Validation.MESSAGE_SPECIFIED));
    }
}
