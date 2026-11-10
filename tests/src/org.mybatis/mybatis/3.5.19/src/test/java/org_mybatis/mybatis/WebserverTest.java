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
import java.nio.charset.StandardCharsets;

import org.apache.ibatis.javassist.bytecode.ClassFile;
import org.apache.ibatis.javassist.tools.web.Webserver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebserverTest {
    @Test
    void servesAnExistingClassResourceAsAnHttpResponse() throws Exception {
        Webserver server = new Webserver(0);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            server.doReply(new ByteArrayInputStream(new byte[0]), output,
                    "GET /org/apache/ibatis/session/RowBounds.class HTTP/1.0");
            byte[] response = output.toByteArray();
            String text = new String(response, StandardCharsets.ISO_8859_1);
            int bodyOffset = text.indexOf("\r\n\r\n") + 4;
            assertTrue(text.startsWith("HTTP/1.0 200 OK\r\n"));
            assertTrue(text.contains("Content-Type: application/octet-stream"));
            assertTrue(bodyOffset >= 4);
            try (DataInputStream input = new DataInputStream(
                    new ByteArrayInputStream(response, bodyOffset, response.length - bodyOffset))) {
                ClassFile definition = new ClassFile(input);
                assertEquals("org.apache.ibatis.session.RowBounds", definition.getName());
                assertEquals("java.lang.Object", definition.getSuperclass());
            }
        } finally {
            server.end();
        }
    }
}
