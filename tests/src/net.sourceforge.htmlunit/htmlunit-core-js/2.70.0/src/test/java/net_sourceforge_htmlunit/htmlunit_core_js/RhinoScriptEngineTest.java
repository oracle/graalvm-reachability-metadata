/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.io.File;
import java.io.FilenameFilter;
import javax.script.Invocable;
import javax.script.ScriptEngine;

import net.sourceforge.htmlunit.corejs.javascript.engine.RhinoScriptEngine;
import net.sourceforge.htmlunit.corejs.javascript.engine.RhinoScriptEngineFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RhinoScriptEngineTest {
    @Test
    void exposesGlobalFunctionsAsFilenameFilterProxy() throws Exception {
        ScriptEngine engine = newInterpretedEngine();
        engine.eval("""
                function accept(dir, name) {
                    var fileName = String(name);
                    return fileName.indexOf('global-') == 0;
                }
                """);

        FilenameFilter filter = ((Invocable) engine).getInterface(FilenameFilter.class);

        assertThat(filter).isNotNull();
        assertThat(filter.accept(new File("ignored"), "global-script.js")).isTrue();
        assertThat(filter.accept(new File("ignored"), "local-script.js")).isFalse();
    }

    @Test
    void exposesScriptObjectFunctionsAsFilenameFilterProxy() throws Exception {
        ScriptEngine engine = newInterpretedEngine();
        Object filterObject = engine.eval("""
                ({
                    accept: function(dir, name) {
                        var fileName = String(name);
                        return fileName.lastIndexOf('.js') == fileName.length - 3;
                    }
                });
                """);

        FilenameFilter filter =
                ((Invocable) engine).getInterface(filterObject, FilenameFilter.class);

        assertThat(filter).isNotNull();
        assertThat(filter.accept(new File("ignored"), "script.js")).isTrue();
        assertThat(filter.accept(new File("ignored"), "README.md")).isFalse();
    }

    @Test
    void returnsNullWhenInterfaceMethodIsMissing() throws Exception {
        ScriptEngine engine = newInterpretedEngine();
        engine.eval("""
                function differentName() {
                    return 'not Runnable.run';
                }
                """);

        Runnable runnable = ((Invocable) engine).getInterface(Runnable.class);

        assertThat(runnable).isNull();
    }

    private static ScriptEngine newInterpretedEngine() {
        ScriptEngine engine = new RhinoScriptEngineFactory().getScriptEngine();
        engine.put(RhinoScriptEngine.OPTIMIZATION_LEVEL, -1);
        return engine;
    }
}
