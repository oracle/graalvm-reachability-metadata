/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.mbeans.MBeanFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MBeanFactoryTest {

    @Test
    void createsValveForManagedEngine() throws Exception {
        StandardEngine engine = new StandardEngine();
        engine.setName("managed-engine");
        engine.setDomain("factory-domain");
        StandardService service = new StandardService();
        service.setContainer(engine);
        MBeanFactory factory = new MBeanFactory();
        factory.setContainer(service);

        String objectName = factory.createValve(RecordingValve.class.getName(),
                "factory-domain:type=Engine");

        assertThat(objectName).isNull();
        assertThat(engine.getPipeline().getValves()).anyMatch(RecordingValve.class::isInstance);
    }

    public static final class RecordingValve implements Valve {
        private Valve next;

        public RecordingValve() {
        }

        @Override
        public Valve getNext() {
            return next;
        }

        @Override
        public void setNext(Valve valve) {
            next = valve;
        }

        @Override
        public void backgroundProcess() {
        }

        @Override
        public void invoke(Request request, Response response) {
        }

        @Override
        public boolean isAsyncSupported() {
            return true;
        }
    }
}
