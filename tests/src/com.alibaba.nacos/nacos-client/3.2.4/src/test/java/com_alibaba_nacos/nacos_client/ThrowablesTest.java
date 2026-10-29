/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import com.alibaba.nacos.shaded.com.google.common.base.Throwables;
import org.junit.jupiter.api.Test;

public class ThrowablesTest {
    @Test
    void exposesTheThrowableStackThroughTheLazyStackTraceApi() {
        Exception failure = new Exception("config");
        assertArrayEquals(failure.getStackTrace(), Throwables.lazyStackTrace(failure).toArray(new StackTraceElement[0]));
    }
}
