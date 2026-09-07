/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.math.BigInteger;
import java.security.SecureRandom;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.trilead.ssh2.crypto.dh.DhExchange;

import static org.assertj.core.api.Assertions.assertThat;

public class DhExchangeTest {
    @Test
    void completesAGroup14DiffieHellmanExchange() {
        DhExchange exchange = new DhExchange();
        exchange.init(14, new SecureRandom());
        BigInteger publicValue = exchange.getE();
        exchange.setF(publicValue);

        assertThat(publicValue.signum()).isPositive();
        assertThat(exchange.getK().signum()).isPositive();
    }
}
