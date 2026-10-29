/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.alibaba.nacos.shaded.com.google.common.util.concurrent.Futures;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class FuturesGetCheckedTest {
    @Test
    void convertsFailedFuturesToTheRequestedCheckedException() throws Exception {
        IOException cause = new IOException("configuration unavailable");
        IOException failure = assertThrows(IOException.class,
                () -> Futures.getChecked(Futures.immediateFailedFuture(cause), IOException.class));
        assertSame(cause, failure.getCause());
        assertEquals("ready", Futures.getChecked(Futures.immediateFuture("ready"), IOException.class));
    }
}
