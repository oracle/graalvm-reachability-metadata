/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.rel.externalize.RelJson;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.util.JsonBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RelJsonTest {
    @Test
    void resolvesShortAndQualifiedRelationTypeNames() {
        RelJson relJson = new RelJson(new JsonBuilder());

        assertThat(relJson.typeNameToClass("LogicalValues")).isEqualTo(LogicalValues.class);
        assertThat(relJson.typeNameToClass(LogicalProject.class.getName())).isEqualTo(LogicalProject.class);
        assertThat(relJson.getConstructor("LogicalValues").getDeclaringClass())
                .isEqualTo(LogicalValues.class);
    }
}
