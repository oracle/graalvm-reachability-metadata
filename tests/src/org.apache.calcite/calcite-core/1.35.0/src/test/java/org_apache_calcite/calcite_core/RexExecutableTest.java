/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.DataContexts;
import org.apache.calcite.rex.RexExecutable;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class RexExecutableTest {
    @Test
    public void compilesGeneratedReducerAndExecutesAgainstDataContext() {
        String code = """
            public Object[] apply(Object root) {
              org.apache.calcite.DataContext context = (org.apache.calcite.DataContext) root;
              return new Object[] {context.get(\"name\"), context.get(\"answer\")};
            }
            """;
        RexExecutable executable = new RexExecutable(code, "data context projection");

        executable.setDataContext(DataContexts.of(Map.of(
            "name", "calcite",
            "answer", 42)));

        assertThat(executable.getSource()).isEqualTo(code);
        assertThat(executable.execute()).containsExactly("calcite", 42);
    }
}
