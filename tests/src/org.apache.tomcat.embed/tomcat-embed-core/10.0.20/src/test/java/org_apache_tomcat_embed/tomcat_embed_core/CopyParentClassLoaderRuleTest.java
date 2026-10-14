/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.CopyParentClassLoaderRule;
import org.apache.tomcat.util.digester.Digester;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CopyParentClassLoaderRuleTest {

    @Test
    void copiesTheParentContainerClassLoaderToItsChild() throws Exception {
        ClassLoader parentLoader = getClass().getClassLoader();
        StandardEngine engine = new StandardEngine();
        engine.setParentClassLoader(parentLoader);
        Digester digester = new Digester();
        digester.push(engine);
        digester.addObjectCreate("Host", StandardHost.class.getName());
        digester.addRule("Host", new CopyParentClassLoaderRule());
        digester.addSetNext("Host", "addChild", "org.apache.catalina.Container");

        digester.parse(new ByteArrayInputStream("<Host/>".getBytes(StandardCharsets.UTF_8)));

        assertThat(engine.findChildren()).hasSize(1);
        assertThat(engine.findChildren()[0]).isInstanceOf(StandardHost.class);
        assertThat(engine.findChildren()[0].getParentClassLoader()).isSameAs(parentLoader);
    }
}
