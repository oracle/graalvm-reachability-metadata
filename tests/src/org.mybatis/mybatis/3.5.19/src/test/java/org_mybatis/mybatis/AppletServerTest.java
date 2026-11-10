/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.ibatis.javassist.bytecode.ClassFile;
import org.apache.ibatis.javassist.bytecode.CodeIterator;
import org.apache.ibatis.javassist.bytecode.MethodInfo;
import org.apache.ibatis.javassist.bytecode.Opcode;
import org.apache.ibatis.javassist.tools.rmi.AppletServer;
import org.apache.ibatis.javassist.tools.rmi.RemoteRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AppletServerTest {
    @Test
    @Timeout(60)
    void exportsAServiceAndDispatchesSerializedRemoteArguments() throws Exception {
        AppletServer server = new AppletServer(0);
        try {
            GreetingService service = new GreetingService();
            int objectId = server.exportObject("greeting", service);
            ByteArrayOutputStream lookup = new ByteArrayOutputStream();
            try (ObjectOutputStream request = new ObjectOutputStream(lookup)) {
                request.writeUTF("greeting");
            }
            try (ObjectInputStream response = new ObjectInputStream(
                    reply(server, "POST /lookup HTTP/1.0", lookup.toByteArray()))) {
                assertEquals(objectId, response.readInt());
                assertEquals(GreetingService.class.getName(), response.readUTF());
            }

            // Read the wire method identifier from the served stub without defining a class.
            int methodId = greetingMethodId(server);
            ByteArrayOutputStream invocation = new ByteArrayOutputStream();
            try (ObjectOutputStream request = new ObjectOutputStream(invocation)) {
                request.writeInt(objectId);
                request.writeInt(methodId);
                request.writeInt(2);
                request.writeObject("Ada");
                request.writeObject(new RemoteRef(objectId));
            }
            try (ObjectInputStream response = new ObjectInputStream(
                    reply(server, "POST /rmi HTTP/1.0", invocation.toByteArray()))) {
                assertTrue(response.readBoolean());
                assertEquals("Hello, Ada", response.readObject());
            }
            assertEquals("Ada", service.lastName);
            assertSame(service, service.lastPeer);
        } finally {
            server.end();
        }
    }

    private static ByteArrayInputStream reply(AppletServer server, String command, byte[] request) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        server.doReply(new ByteArrayInputStream(request), output, command);
        byte[] response = output.toByteArray();
        String text = new String(response, StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("HTTP/1.0 200 OK\r\n"));
        int bodyOffset = text.indexOf("\r\n\r\n") + 4;
        assertTrue(bodyOffset >= 4);
        return new ByteArrayInputStream(response, bodyOffset, response.length - bodyOffset);
    }

    private static int greetingMethodId(AppletServer server) throws Exception {
        String path = GreetingService.class.getName().replace('.', '/') + ".class";
        try (DataInputStream input = new DataInputStream(reply(server, "GET /" + path + " HTTP/1.0", new byte[0]))) {
            ClassFile stub = new ClassFile(input);
            assertEquals(GreetingService.class.getName(), stub.getName());
            for (MethodInfo method : stub.getMethods()) {
                if ("greet".equals(method.getName())) {
                    CodeIterator code = method.getCodeAttribute().iterator();
                    int previous = -1;
                    while (code.hasNext()) {
                        int position = code.next();
                        if (code.byteAt(position) == Opcode.INVOKESPECIAL) {
                            assertTrue(previous >= 0);
                            int opcode = code.byteAt(previous);
                            if (opcode >= Opcode.ICONST_0 && opcode <= Opcode.ICONST_5) {
                                return opcode - Opcode.ICONST_0;
                            }
                            if (opcode == Opcode.BIPUSH) {
                                return code.signedByteAt(previous + 1);
                            }
                            if (opcode == Opcode.SIPUSH) {
                                return code.s16bitAt(previous + 1);
                            }
                            throw new AssertionError("Expected a method identifier before the stub's forwarding call");
                        }
                        previous = position;
                    }
                }
            }
            throw new AssertionError("The served stub must forward greet calls");
        }
    }

    public static class GreetingService {
        private String lastName;
        private GreetingService lastPeer;

        public String greet(String name, GreetingService peer) {
            lastName = name;
            lastPeer = peer;
            return "Hello, " + name;
        }
    }
}
