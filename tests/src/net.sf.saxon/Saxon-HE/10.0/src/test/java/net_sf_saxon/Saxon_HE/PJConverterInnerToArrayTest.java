/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.PJConverter;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.type.BuiltInAtomicType;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class PJConverterInnerToArrayTest {
    @Test
    void convertsXdmStringSequenceToJavaArray() throws Exception {
        Configuration configuration = Configuration.newConfiguration();
        PJConverter converter = PJConverter.allocate(
                configuration,
                BuiltInAtomicType.STRING,
                StaticProperty.ALLOWS_ZERO_OR_MORE,
                String[].class);
        XdmValue source = new XdmValue(Arrays.<XdmItem>asList(
                new XdmAtomicValue("alpha"),
                new XdmAtomicValue("beta")));
        GroundedValue underlyingValue = source.getUnderlyingValue();

        Object converted = converter.convert(underlyingValue, String[].class, null);

        assertThat(converted).isInstanceOf(String[].class);
        assertThat((String[]) converted).containsExactly("alpha", "beta");
    }
}
