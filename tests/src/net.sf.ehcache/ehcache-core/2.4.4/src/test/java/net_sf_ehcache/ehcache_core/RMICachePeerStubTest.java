/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.Operation;
import java.rmi.server.RemoteCall;
import java.rmi.server.RemoteObject;
import java.rmi.server.RemoteRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.ehcache.Element;
import net.sf.ehcache.distribution.RMICachePeer_Stub;

import org.junit.jupiter.api.Test;

public class RMICachePeerStubTest {
    @Test
    void forwardsCachePeerCallsThroughRemoteRef() throws Exception {
        RecordingRemoteRef remoteRef = new RecordingRemoteRef();
        RMICachePeer_Stub stub = new RMICachePeer_Stub(remoteRef);
        Element element = new Element("cache-key", "cache-value");
        List<String> keys = Collections.singletonList("cache-key");
        List<Element> elements = Collections.singletonList(element);

        remoteRef.returnValue("getName", "replicated-cache");
        remoteRef.returnValue("getGuid", "peer-guid");
        remoteRef.returnValue("getUrl", "//localhost:40001/replicated-cache");
        remoteRef.returnValue("getUrlBase", "//localhost:40001");
        remoteRef.returnValue("getKeys", keys);
        remoteRef.returnValue("getElements", elements);
        remoteRef.returnValue("getQuiet", element);
        remoteRef.returnValue("remove", Boolean.TRUE);

        assertThat(stub.getName()).isEqualTo("replicated-cache");
        assertThat(stub.getGuid()).isEqualTo("peer-guid");
        assertThat(stub.getUrl()).isEqualTo("//localhost:40001/replicated-cache");
        assertThat(stub.getUrlBase()).isEqualTo("//localhost:40001");
        assertThat(stub.getKeys()).containsExactly("cache-key");
        assertThat(stub.getElements(keys)).containsExactly(element);
        assertThat(stub.getQuiet("cache-key")).isSameAs(element);
        assertThat(stub.remove("cache-key")).isTrue();

        stub.put(element);
        stub.removeAll();
        stub.send(Collections.emptyList());

        assertThat(remoteRef.invokedMethods()).containsExactly(
                "getName",
                "getGuid",
                "getUrl",
                "getUrlBase",
                "getKeys",
                "getElements",
                "getQuiet",
                "remove",
                "put",
                "removeAll",
                "send");
    }

    private static final class RecordingRemoteRef implements RemoteRef {
        private final List<String> invokedMethods = new ArrayList<String>();
        private final Map<String, Object> returnValues = new HashMap<String, Object>();

        private void returnValue(String methodName, Object returnValue) {
            returnValues.put(methodName, returnValue);
        }

        private List<String> invokedMethods() {
            return invokedMethods;
        }

        @Override
        public Object invoke(Remote remote, Method method, Object[] params, long opnum) {
            String methodName = method.getName();
            invokedMethods.add(methodName);
            return returnValues.get(methodName);
        }

        @Override
        public RemoteCall newCall(RemoteObject obj, Operation[] operations, int opnum, long hash)
                throws RemoteException {
            throw new RemoteException("The generated stub should use RemoteRef.invoke on this JDK");
        }

        @Override
        public void invoke(RemoteCall call) throws Exception {
            throw new RemoteException("The generated stub should use RemoteRef.invoke on this JDK");
        }

        @Override
        public void done(RemoteCall call) throws RemoteException {
            // No call resources are allocated by this recording reference.
        }

        @Override
        public String getRefClass(ObjectOutput out) {
            return getClass().getName();
        }

        @Override
        public int remoteHashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean remoteEquals(RemoteRef obj) {
            return this == obj;
        }

        @Override
        public String remoteToString() {
            return getClass().getName() + invokedMethods;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            // The recording reference is never serialized by this test.
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            // The recording reference is never deserialized by this test.
        }
    }
}
