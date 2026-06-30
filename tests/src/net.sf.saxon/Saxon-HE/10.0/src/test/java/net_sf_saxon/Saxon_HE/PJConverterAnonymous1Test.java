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
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.UntypedAtomicValue;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PJConverterAnonymous1Test {
    @Test
    void convertsUntypedAtomicValueToTargetWithStringConstructor() throws Exception {
        Configuration configuration = Configuration.newConfiguration();
        PJConverter converter = PJConverter.allocate(
                configuration,
                BuiltInAtomicType.UNTYPED_ATOMIC,
                StaticProperty.ALLOWS_ONE,
                XdmAtomicValue.class);

        Object converted = converter.convert(
                new UntypedAtomicValue("created reflectively"),
                XdmAtomicValue.class,
                null);

        assertThat(converted).isInstanceOf(XdmAtomicValue.class);
        assertThat(((XdmAtomicValue) converted).getStringValue()).isEqualTo("created reflectively");
    }
}
