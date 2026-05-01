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
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.StringValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PJConverterInnerToArrayTest {
    @Test
    void convertsXPathSequenceToJavaArray() throws Exception {
        Configuration configuration = Configuration.newConfiguration();
        PJConverter converter = PJConverter.allocate(
                configuration,
                BuiltInAtomicType.STRING,
                StaticProperty.ALLOWS_ONE_OR_MORE,
                String[].class);
        SequenceExtent<StringValue> sequence = new SequenceExtent<>(new StringValue[] {
            new StringValue("alpha"),
            new StringValue("beta"),
            new StringValue("gamma")
        });

        Object convertedValue = converter.convert(sequence, String[].class, null);

        assertThat(convertedValue).isInstanceOf(String[].class);
        assertThat((String[]) convertedValue).containsExactly("alpha", "beta", "gamma");
    }
}
