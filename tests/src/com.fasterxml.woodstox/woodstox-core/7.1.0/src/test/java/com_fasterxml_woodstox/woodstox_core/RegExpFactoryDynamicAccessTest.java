/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.datatype.xsd.regex.RegExp;
import com.ctc.wstx.shaded.msv_core.datatype.xsd.regex.RegExpFactory;
import org.junit.jupiter.api.Test;

public class RegExpFactoryDynamicAccessTest {
    @Test
    void createsARegexpImplementationReflectively() throws Exception {
        RegExpFactory factory = RegExpFactory.createFactory();
        RegExp regExp = factory.compile("[a-z]+[0-9]?");

        assertThat(factory).isNotNull();
        assertThat(regExp.matches("woodstox7")).isTrue();
        assertThat(regExp.matches("WOODSTOX")).isFalse();
    }
}
