/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package gg_jte.jte_runtime;

import gg.jte.TemplateOutput;
import gg.jte.html.HtmlInterceptor;
import java.util.Map;

public class JteJte_runtimeNoParametersTestGenerated {
    public static final String JTE_NAME = "no-parameters.jte";
    public static final int[] JTE_LINE_INFO = new int[0];

    public static String templateName() {
        return "Jte_runtimeNoParametersTest";
    }

    public static void render(TemplateOutput output, HtmlInterceptor interceptor) {
        output.writeContent("rendered without parameters");
    }

    public static void renderMap(
            TemplateOutput output, HtmlInterceptor interceptor, Map<String, Object> parameters) {
        output.writeContent("rendered map without parameters");
    }
}
