/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_puppycrawl_tools.checkstyle;

import static org.assertj.core.api.Assertions.assertThat;

import com.puppycrawl.tools.checkstyle.TreeWalker;
import com.puppycrawl.tools.checkstyle.utils.ModuleReflectionUtil;
import org.junit.jupiter.api.Test;

public class ModuleReflectionUtilTest {
    @Test
    void recognizesModuleWithDefaultConstructorAsValidCheckstyleClass() {
        boolean validCheckstyleClass = ModuleReflectionUtil.isValidCheckstyleClass(TreeWalker.class);

        assertThat(validCheckstyleClass).isTrue();
    }
}
