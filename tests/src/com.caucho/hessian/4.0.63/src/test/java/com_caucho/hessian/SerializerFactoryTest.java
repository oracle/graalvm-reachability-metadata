/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import com.caucho.hessian.io.SerializerFactory;
import java.io.File;
import java.math.BigDecimal;
import java.util.Locale;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class SerializerFactoryTest {
    @Test
    void discoversBundledCodecsForStandardValues() throws Exception {
        for (Object value : new Object[] {new BigDecimal("12345.6700"), new File("snapshot.bin"),
                new ObjectName("consistency:type=Snapshot,name=primary"), Locale.CANADA_FRENCH}) {
            for (boolean hessian2 : new boolean[] {false, true}) {
                assertEquals(value, SerializationSupport.roundTrip(value, new SerializerFactory(), hessian2));
            }
        }
    }
}
