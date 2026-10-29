/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.api.ability.ClientAbilities;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

public class ClientAbilitiesTest {
    @Test
    void serializesNestedClientCapabilityNegotiationData() {
        ClientAbilities abilities = new ClientAbilities();
        abilities.getConfigAbility().setSupportRemoteMetrics(true);
        ClientAbilities restored = JacksonUtils.toObj(JacksonUtils.toJsonBytes(abilities), ClientAbilities.class);
        assertTrue(restored.getConfigAbility().isSupportRemoteMetrics());
        assertNotNull(restored.getRemoteAbility());
        assertNotNull(restored.getNamingAbility());
    }
}
