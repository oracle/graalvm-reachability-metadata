/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

import javassist.bytecode.ClassFile;
import javassist.tools.web.Webserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebserverTest {
    @TempDir
    Path directory;

    @Test
    void servesLibraryClassResourcesWhenNoFileIsPresent() throws Exception {
        Webserver server = new Webserver(0);
        try {
            server.htmlfileBase = directory.toString() + "/";
            ByteArrayOutputStream reply = new ByteArrayOutputStream();
            server.doReply(new ByteArrayInputStream(new byte[0]), reply,
                    "GET /javassist/bytecode/ClassFile.class HTTP/1.0");
            byte[] response = reply.toByteArray();
            String text = new String(response, StandardCharsets.ISO_8859_1);
            int bodyOffset = text.indexOf("\r\n\r\n") + 4;
            assertTrue(bodyOffset >= 4);
            byte[] body = Arrays.copyOfRange(response, bodyOffset, response.length);
            String header = text.substring(0, bodyOffset);
            assertTrue(header.startsWith("HTTP/1.0 200 OK\r\n"));
            assertTrue(header.contains("Content-Type: application/octet-stream\r\n"));
            assertTrue(header.contains("Content-Length: " + body.length + "\r\n"));
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(body))) {
                ClassFile file = new ClassFile(input);
                assertEquals("javassist.bytecode.ClassFile", file.getName());
                assertNotNull(file.getMethod("getName"));
            }
        } finally {
            server.end();
        }
    }
}
