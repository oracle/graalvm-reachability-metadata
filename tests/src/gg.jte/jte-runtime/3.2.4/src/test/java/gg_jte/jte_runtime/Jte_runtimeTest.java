/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package gg_jte.jte_runtime;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;

public final class Jte_runtimeTest {
    private Jte_runtimeTest() {
    }

    static TemplateEngine precompiledEngine() {
        return TemplateEngine.createPrecompiled(
                null,
                ContentType.Plain,
                Jte_runtimeTest.class.getClassLoader(),
                "gg_jte.jte_runtime");
    }

}
