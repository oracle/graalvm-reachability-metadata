/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.servlets.WebdavServlet;
import org.apache.catalina.servlets.WebdavServlet.ProppatchOperation;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.util.XMLWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

public class WebdavServletTest {

    @Test
    void initializesConfiguredPropertyStore(@TempDir Path directory) throws Exception {
        RecordingPropertyStore.INITIALIZED.set(false);
        RecordingPropertyStore.DESTROYED.set(false);
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("base").toString());
        Context context = tomcat.addContext("/dav", directory.toString());
        Wrapper wrapper = Tomcat.addServlet(context, "webdav", new WebdavServlet());
        wrapper.addInitParameter("propertyStore", RecordingPropertyStore.class.getName());
        wrapper.setLoadOnStartup(1);
        context.addServletMappingDecoded("/*", "webdav");

        try {
            tomcat.start();
            assertThat(RecordingPropertyStore.INITIALIZED).isTrue();
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }

        assertThat(RecordingPropertyStore.DESTROYED).isTrue();
    }

    public static class RecordingPropertyStore implements WebdavServlet.PropertyStore {
        static final AtomicBoolean INITIALIZED = new AtomicBoolean();
        static final AtomicBoolean DESTROYED = new AtomicBoolean();

        @Override
        public void init() {
            INITIALIZED.set(true);
        }

        @Override
        public void destroy() {
            DESTROYED.set(true);
        }

        @Override
        public void periodicEvent() {
        }

        @Override
        public void copy(String source, String destination) {
        }

        @Override
        public void delete(String resource) {
        }

        @Override
        public boolean propfind(String resource, Node property, boolean nameOnly, XMLWriter generatedXML) {
            return false;
        }

        @Override
        public void proppatch(String resource, ArrayList<ProppatchOperation> operations) {
        }
    }
}
