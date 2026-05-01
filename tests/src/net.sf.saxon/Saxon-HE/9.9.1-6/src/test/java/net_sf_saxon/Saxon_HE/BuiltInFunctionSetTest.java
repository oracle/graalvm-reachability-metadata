/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BuiltInFunctionSetTest {
    @Test
    void xpathEvaluationInstantiatesBuiltInFunctionImplementations() throws Exception {
        Processor processor = new Processor(false);
        XPathCompiler compiler = processor.newXPathCompiler();

        XdmValue result = compiler.evaluate("concat('Saxon', '-', upper-case('he'))", null);

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.itemAt(0).getStringValue()).isEqualTo("Saxon-HE");
    }
}
