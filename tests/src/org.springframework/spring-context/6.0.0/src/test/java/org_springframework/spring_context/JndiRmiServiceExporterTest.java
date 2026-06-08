/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.rmi.Remote;
import java.rmi.RemoteException;

import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;

import org.junit.jupiter.api.Test;

import org.springframework.jndi.JndiTemplate;
import org.springframework.remoting.rmi.JndiRmiServiceExporter;

@SuppressWarnings("deprecation")
public class JndiRmiServiceExporterTest {

    @Test
    void preparesAndDestroysRemoteServiceThroughPortableRemoteObject() throws Exception {
        PortableRemoteObject.reset();

        final RecordingJndiTemplate jndiTemplate = new RecordingJndiTemplate();
        final TestRemoteService service = new TestRemoteService();
        final JndiRmiServiceExporter exporter = new JndiRmiServiceExporter();
        exporter.setJndiTemplate(jndiTemplate);
        exporter.setJndiName("rmi/testService");
        exporter.setService(service);

        exporter.prepare();
        exporter.destroy();

        assertEquals("rmi/testService", jndiTemplate.reboundName);
        assertSame(service, jndiTemplate.reboundObject);
        assertEquals("rmi/testService", jndiTemplate.unboundName);
        assertEquals(1, PortableRemoteObject.exportedObjects().size());
        assertEquals(1, PortableRemoteObject.unexportedObjects().size());
        assertSame(service, PortableRemoteObject.exportedObjects().get(0));
        assertSame(service, PortableRemoteObject.unexportedObjects().get(0));
    }

    private interface TestRemote extends Remote {

        String ping() throws RemoteException;
    }

    private static final class TestRemoteService implements TestRemote {

        @Override
        public String ping() throws RemoteException {
            return "pong";
        }
    }

    private static final class RecordingJndiTemplate extends JndiTemplate {

        private String reboundName;
        private Object reboundObject;
        private String unboundName;

        @Override
        public void rebind(String name, Object object) throws NamingException {
            this.reboundName = name;
            this.reboundObject = object;
        }

        @Override
        public void unbind(String name) throws NamingException {
            this.unboundName = name;
        }
    }
}
