/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_shell.spring_shell_jline;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;
import org.springframework.shell.jline.tui.table.BorderSpecification;
import org.springframework.shell.jline.tui.table.BorderStyle;

public class BorderSpecificationTest {

    @Test
    void toStringDescribesNamedConstantMatch() throws Exception {
        BorderSpecification specification = newBorderSpecification(BorderSpecification.FULL);

        assertThat(specification.toString())
                .contains("BorderSpecification[(0, 0)->(2, 2)")
                .contains(BorderStyle.fancy_light.toString())
                .endsWith("FULL]");
    }

    @Test
    void toStringDescribesCombinedBitMaskMatch() throws Exception {
        int match = BorderSpecification.TOP | BorderSpecification.LEFT;
        BorderSpecification specification = newBorderSpecification(match);

        assertThat(specification.toString())
                .contains("BorderSpecification[(0, 0)->(2, 2)")
                .contains(BorderStyle.fancy_light.toString())
                .endsWith("TOP|LEFT]");
    }

    private static BorderSpecification newBorderSpecification(int match) throws Exception {
        Constructor<BorderSpecification> constructor = BorderSpecification.class.getDeclaredConstructor(
                int.class, int.class, int.class, int.class, int.class, BorderStyle.class);
        constructor.setAccessible(true);
        return constructor.newInstance(0, 0, 2, 2, match, BorderStyle.fancy_light);
    }
}
