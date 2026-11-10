/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ldap.spring_ldap_core;

import org.junit.jupiter.api.Test;
import org.springframework.ldap.odm.typeconversion.impl.converters.FromStringConverter;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class FromStringConverterTest {
    @Test
    void convertsAnAttributeUsingItsPublicStringConstructor() throws Exception {
        FromStringConverter converter = new FromStringConverter();
        BigInteger number = converter.convert("12345678901234567890", BigInteger.class);
        assertThat(number.add(BigInteger.ONE)).isEqualTo(new BigInteger("12345678901234567891"));
    }
}
