/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_api;

import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.healthcheck.HealthCheckerFactory;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Mysql;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Tcp;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class HealthCheckerFactoryTest {
    @Test
    void decodesAndRoundTripsHttpHealthChecks() {
        Http checker = assertInstanceOf(Http.class, HealthCheckerFactory.deserialize("""
                {"type":"HTTP","path":"/ready","headers":"X-Probe: nacos","expectedResponseCode":204}
                """));

        assertEquals("/ready", checker.getPath());
        assertEquals(204, checker.getExpectedResponseCode());
        assertEquals(Map.of("X-Probe", "nacos"), checker.getCustomHeaders());
        assertEquals(checker, HealthCheckerFactory.deserialize(HealthCheckerFactory.serialize(checker)));
    }

    @Test
    void decodesAndRoundTripsMysqlHealthChecks() throws CloneNotSupportedException {
        Mysql checker = assertInstanceOf(Mysql.class, HealthCheckerFactory.deserialize("""
                {"type":"MYSQL","user":"probe","pwd":"probe-secret","cmd":"SELECT 1"}
                """));

        assertEquals("probe", checker.getUser());
        assertEquals("probe-secret", checker.getPwd());
        assertEquals("SELECT 1", checker.getCmd());
        assertEquals(checker, HealthCheckerFactory.deserialize(HealthCheckerFactory.serialize(checker)));
        Mysql copy = checker.clone();
        assertNotSame(checker, copy);
        assertEquals(checker, copy);
        copy.setCmd("SELECT 2");
        assertEquals("SELECT 1", checker.getCmd());
    }

    @Test
    void decodesTcpAndDisabledHealthChecks() {
        Tcp tcp = assertInstanceOf(Tcp.class, HealthCheckerFactory.deserialize("""
                {"type":"TCP"}
                """));
        assertEquals("TCP", tcp.getType());
        assertEquals(tcp, HealthCheckerFactory.deserialize(HealthCheckerFactory.serialize(tcp)));

        AbstractHealthChecker.None none = assertInstanceOf(AbstractHealthChecker.None.class,
                HealthCheckerFactory.deserialize("""
                        {"type":"NONE"}
                        """));
        AbstractHealthChecker restored = HealthCheckerFactory.deserialize(HealthCheckerFactory.serialize(none));
        assertInstanceOf(AbstractHealthChecker.None.class, restored);
        assertEquals("NONE", restored.getType());
    }
}
