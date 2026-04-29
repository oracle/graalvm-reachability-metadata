/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.calcite.DataContexts;
import org.apache.calcite.rex.RexExecutable;
import org.junit.jupiter.api.Test;

public class RexExecutableTest {
    @Test
    void compilesGeneratedFunctionAndExecutesWithDataContext() {
        String code = """
                public Object[] apply(Object root0) {
                  final org.apache.calcite.DataContext root =
                      (org.apache.calcite.DataContext) root0;
                  return new Object[] {
                      root.get("inputValue"),
                      Integer.valueOf(35)
                  };
                }
                """;
        RexExecutable executable = new RexExecutable(code, "generated test expression");
        executable.setDataContext(DataContexts.of(Map.of("inputValue", "calcite")));

        Object[] values = executable.execute();

        assertThat(values).containsExactly("calcite", 35);
        assertThat(executable.getSource()).isEqualTo(code);
        assertThat(executable.getFunction()).isNotNull();
    }
}
