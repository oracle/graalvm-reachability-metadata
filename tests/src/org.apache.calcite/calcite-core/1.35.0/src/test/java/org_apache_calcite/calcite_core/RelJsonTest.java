/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import org.apache.calcite.rel.RelInput;
import org.apache.calcite.rel.externalize.RelJson;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalValues;
import org.junit.jupiter.api.Test;

public class RelJsonTest {
    @Test
    void resolvesShortAndQualifiedRelTypeNames() {
        RelJson relJson = RelJson.create();

        assertThat(relJson.typeNameToClass("LogicalValues"))
                .isEqualTo(LogicalValues.class);
        assertThat(relJson.typeNameToClass(LogicalFilter.class.getName()))
                .isEqualTo(LogicalFilter.class);
    }

    @Test
    void discoversRelInputConstructorForShortTypeName() {
        RelJson relJson = RelJson.create();

        Constructor<?> constructor = relJson.getConstructor("LogicalValues");

        assertThat(constructor.getDeclaringClass()).isEqualTo(LogicalValues.class);
        assertThat(constructor.getParameterTypes()).containsExactly(RelInput.class);
    }

    @Test
    void createAttemptsToInstantiateRelNodeUsingResolvedConstructor() {
        RelJson relJson = RelJson.create();
        Map<String, Object> jsonRel = new HashMap<>();
        jsonRel.put("type", "LogicalValues");

        assertThatThrownBy(() -> relJson.create(jsonRel))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
