/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.api.ability.ServerAbilities;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

public class ServerAbilitiesTest {
    @Test
    void serializesNestedServerCapabilityNegotiationData() {
        ServerAbilities abilities = new ServerAbilities();
        abilities.getConfigAbility().setSupportRemoteMetrics(true);
        ServerAbilities restored = JacksonUtils.toObj(JacksonUtils.toJson(abilities), ServerAbilities.class);
        assertTrue(restored.getConfigAbility().isSupportRemoteMetrics());
        assertEquals(abilities, restored);
    }
}
