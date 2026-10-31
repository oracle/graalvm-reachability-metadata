/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GeneratedMetaMethodInnerProxyTest {
    @Test
    void invokesPackagedExtensionMethodImplementations() {
        assertEquals("red:blue", InvokerHelper.invokeMethod(List.of("red", "blue"), "join", new Object[]{":"}));
        assertEquals(12, InvokerHelper.invokeMethod(List.of(3, 4, 5), "sum", new Object[]{}));
    }
}
