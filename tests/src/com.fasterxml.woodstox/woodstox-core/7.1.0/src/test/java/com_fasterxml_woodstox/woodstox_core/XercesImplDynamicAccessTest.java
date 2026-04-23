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
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

public class XercesImplDynamicAccessTest {
    private static final String FACTORY_CLASS_NAME =
            "com.ctc.wstx.shaded.msv_core.datatype.xsd.regex.XercesImpl";

    @Test
    void compilesPatternsThroughTheXercesRegexpImplementation() throws Exception {
        RegExpFactory factory = newFactory();
        RegExp regExp = factory.compile("[A-Z]{2,5}-[0-9]{2}");

        assertThat(regExp.matches("WSTX-71")).isTrue();
        assertThat(regExp.matches("woodstox-71")).isFalse();
    }

    private static RegExpFactory newFactory() throws Exception {
        Class<?> factoryClass = Class.forName(FACTORY_CLASS_NAME);
        Constructor<?> constructor = factoryClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (RegExpFactory) constructor.newInstance();
    }
}
