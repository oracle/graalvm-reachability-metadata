/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.io.StringReader;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.executor.loader.javassist.JavassistProxyFactory;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BaseBuilderTest {
    @Test
    void instantiatesConfiguredProxyFactory() {
        String xml = """
                <!DOCTYPE configuration PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
                    "https://mybatis.org/dtd/mybatis-3-config.dtd">
                <configuration>
                  <settings>
                    <setting name="proxyFactory" value="org_mybatis.mybatis.BaseBuilderTest$ProxyFactory"/>
                  </settings>
                </configuration>
                """;
        Configuration configuration = new XMLConfigBuilder(new StringReader(xml)).parse();
        ProxyFactory factory = assertInstanceOf(ProxyFactory.class, configuration.getProxyFactory());
        assertEquals("configured", factory.label());
    }

    public static class ProxyFactory extends JavassistProxyFactory {
        public String label() {
            return "configured";
        }
    }
}
