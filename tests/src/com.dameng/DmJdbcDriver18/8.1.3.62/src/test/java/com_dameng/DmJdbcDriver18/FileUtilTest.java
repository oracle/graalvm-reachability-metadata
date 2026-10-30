/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_dameng.DmJdbcDriver18;

import dm.jdbc.util.FileUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileUtilTest {
    private static final String MESSAGES = "dm/jdbc/resource/messages.properties";

    @Test
    void readsDriverResourcesAsBytesAndText() throws Exception {
        byte[] contents = FileUtil.readByteArrayFromResource(MESSAGES);
        assertTrue(new String(contents, StandardCharsets.ISO_8859_1).contains("error.invalidDbName=EC_INVALID_DB_NAME"));
        assertTrue(FileUtil.readFromResource(MESSAGES).contains("warning.emptyResult=Warning:Empty resultset"));
    }

    @Test
    void fallsBackToLibraryLoaderWhenContextLoaderCannotFindResource() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
            assertTrue(FileUtil.readFromResource(MESSAGES).contains("error.invalidDbName=EC_INVALID_DB_NAME"));
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
