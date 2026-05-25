/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.alibaba.fastjson.util.IOUtils;

import org.junit.jupiter.api.Test;

public class IOUtilsAnonymous1Test {
    @Test
    void loadPropertiesFromFileUsesSystemClassLoaderWhenContextClassLoaderIsAbsent() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(null);

            assertThatCode(IOUtils::loadPropertiesFromFile).doesNotThrowAnyException();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }
}
