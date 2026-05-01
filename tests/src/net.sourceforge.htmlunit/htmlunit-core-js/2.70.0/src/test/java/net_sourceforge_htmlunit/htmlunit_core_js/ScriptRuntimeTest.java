/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.ScriptRuntime;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import net.sourceforge.htmlunit.corejs.javascript.tools.shell.Global;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ScriptRuntimeTest {
    @Test
    void getGlobalCreatesInitializedShellGlobal() {
        Context context = Context.enter();
        try {
            ScriptableObject global = ScriptRuntime.getGlobal(context);

            assertThat(global).isInstanceOf(Global.class);
            assertThat(((Global) global).isInitialized()).isTrue();
            assertThat(ScriptableObject.hasProperty(global, "print")).isTrue();
        } finally {
            Context.exit();
        }
    }
}
