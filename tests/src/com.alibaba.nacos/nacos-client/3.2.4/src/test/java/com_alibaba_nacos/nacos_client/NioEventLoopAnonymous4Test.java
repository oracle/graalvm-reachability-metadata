/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class NioEventLoopAnonymous4Test {
    @Test
    void runsTasksUsingTheNioSelectorLoop() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            assertEquals("completed", group.submit(() -> "completed").get(30, TimeUnit.SECONDS));
        } finally {
            assertTrue(group.shutdownGracefully(0, 30, TimeUnit.SECONDS).await(30, TimeUnit.SECONDS));
        }
    }
}
