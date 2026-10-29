/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.DefaultChannelId;
import org.junit.jupiter.api.Test;

public class DefaultChannelIdTest {
    @Test
    void generatesDistinctStableChannelIdentifiers() {
        DefaultChannelId first = DefaultChannelId.newInstance();
        DefaultChannelId second = DefaultChannelId.newInstance();
        assertNotEquals(first, second);
        assertEquals(8, first.asShortText().length());
        assertEquals(first.asShortText(), first.toString());
        assertTrue(first.asLongText().contains("-"));
    }
}
