/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.resources.css.GenerateCssAst;
import com.google.gwt.resources.css.ast.CssDef;
import com.google.gwt.resources.css.ast.CssNode;
import com.google.gwt.resources.css.ast.CssStylesheet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class GenerateCssAstInnerGenerationHandlerTest {
    @TempDir
    Path tempDirectory;

    @Test
    public void parsesKnownIgnorableAtRuleThroughGeneratedHandler() throws Exception {
        Path stylesheetPath = tempDirectory.resolve("style.css");
        Files.writeString(stylesheetPath, "@def accentColor red;\n");
        URL stylesheetUrl = stylesheetPath.toUri().toURL();

        CssStylesheet stylesheet = GenerateCssAst.exec(TreeLogger.NULL, stylesheetUrl);

        List<CssNode> nodes = stylesheet.getNodes();
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0)).isInstanceOf(CssDef.class);

        CssDef definition = (CssDef) nodes.get(0);
        assertThat(definition.getKey()).isEqualTo("accentColor");
        assertThat(definition.getValues()).hasSize(1);
        assertThat(definition.getValues().get(0).toCss()).isEqualTo("red");
    }
}
