/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js.shellscenarios;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import net.sourceforge.htmlunit.corejs.javascript.tools.shell.ShellConsole;

public class ShellConsoleScenarioRunner implements ShellConsoleScenario {
    @Override
    public String run() throws Exception {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            ScriptableObject nested = new ScriptableObject() {
                @Override
                public String getClassName() {
                    return "nested";
                }
            };
            nested.put("first", nested, "value");
            ScriptableObject.putProperty(scope, "global", nested);

            ShellConsole console = ShellConsole.getConsole(scope, StandardCharsets.UTF_8);
            if (console == null) {
                throw new AssertionError("JLine ShellConsole was not created");
            }

            console.print("prefix");
            console.println("line");
            console.println();
            console.flush();
            String line = console.readLine();
            String promptedLine = console.readLine("prompt> ");
            String streamText;
            try (InputStream in = console.getIn()) {
                streamText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            String consoleType = console.getClass().getName().contains("V1") ? "v1" : "v2";
            return consoleType + "|" + line + "|" + promptedLine + "|" + streamText;
        } finally {
            Context.exit();
        }
    }
}
