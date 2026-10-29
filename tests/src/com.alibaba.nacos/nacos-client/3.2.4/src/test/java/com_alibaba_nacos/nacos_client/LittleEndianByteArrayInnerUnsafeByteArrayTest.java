/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class LittleEndianByteArrayInnerUnsafeByteArrayTest {
    @Test
    void hashesLongInputsUsingFarmHashFingerprint() {
        byte[] data = "configuration-registry-payload-".repeat(8).getBytes(StandardCharsets.UTF_8);
        assertEquals(Hashing.farmHashFingerprint64().hashBytes(data),
                Hashing.farmHashFingerprint64().newHasher().putBytes(data, 0, 80)
                        .putBytes(data, 80, data.length - 80).hash());
    }
}
