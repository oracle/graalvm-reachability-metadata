/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctc.wstx.shaded.msv_core.datatype.xsd.regex.RegExpFactory;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

public class JDK50ImplDynamicAccessTest {
    private static final String FACTORY_CLASS_NAME =
            "com.ctc.wstx.shaded.msv_core.datatype.xsd.regex.JDK50Impl";

    @Test
    void reachesLegacyJdk50RegexpConstructionPathEvenWhenModuleAccessIsRejected() throws Exception {
        RegExpFactory factory = newFactory();

        assertThatThrownBy(() -> factory.compile("[A-Z]{2,5}-[0-9]{2}"))
                .isInstanceOf(IllegalAccessError.class)
                .hasMessageContaining("RegularExpression");
    }

    private static RegExpFactory newFactory() throws Exception {
        Class<?> factoryClass = Class.forName(FACTORY_CLASS_NAME);
        Constructor<?> constructor = factoryClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (RegExpFactory) constructor.newInstance();
    }
}
