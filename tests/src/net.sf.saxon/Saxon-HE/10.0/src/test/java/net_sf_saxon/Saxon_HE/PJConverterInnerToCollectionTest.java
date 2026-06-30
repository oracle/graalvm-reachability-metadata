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
import java.util.LinkedList;

import static org.assertj.core.api.Assertions.assertThat;

public class PJConverterInnerToCollectionTest {
    @Test
    void convertsXdmStringSequenceToRequestedCollectionType() throws Exception {
        Configuration configuration = Configuration.newConfiguration();
        PJConverter converter = PJConverter.allocate(
                configuration,
                BuiltInAtomicType.STRING,
                StaticProperty.ALLOWS_ZERO_OR_MORE,
                LinkedList.class);
        XdmValue source = new XdmValue(Arrays.<XdmItem>asList(
                new XdmAtomicValue("alpha"),
                new XdmAtomicValue("beta")));
        GroundedValue<?> underlyingValue = source.getUnderlyingValue();

        Object converted = converter.convert(
                underlyingValue,
                LinkedList.class,
                configuration.getConversionContext());

        assertThat(converted).isInstanceOf(LinkedList.class);
        LinkedList<?> convertedList = (LinkedList<?>) converted;
        assertThat(convertedList).hasSize(2);
        assertThat(convertedList.get(0)).isEqualTo("alpha");
        assertThat(convertedList.get(1)).isEqualTo("beta");
    }
}
