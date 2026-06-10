/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XPathCompiler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BuiltInFunctionSetTest {
    @Test
    void evaluatesXPathExpressionUsingBuiltInFunction() throws SaxonApiException {
        Processor processor = new Processor(false);
        XPathCompiler compiler = processor.newXPathCompiler();

        XdmItem result = compiler.evaluateSingle("string-length('Saxon')", null);

        assertThat(result).isInstanceOf(XdmAtomicValue.class);
        XdmAtomicValue atomicValue = (XdmAtomicValue) result;
        assertThat(atomicValue.getLongValue()).isEqualTo(5L);
    }
}
