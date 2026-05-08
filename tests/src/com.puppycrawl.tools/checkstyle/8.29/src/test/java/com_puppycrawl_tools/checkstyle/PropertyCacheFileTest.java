/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_puppycrawl_tools.checkstyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.PropertyCacheFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PropertyCacheFileTest {
    @Test
    void loadsAndPersistsCacheForSerializableConfiguration(@TempDir Path tempDir)
            throws Exception {
        DefaultConfiguration configuration = new DefaultConfiguration("Checker");
        configuration.addAttribute("severity", "warning");
        configuration.addMessage("missing", "Custom message");
        configuration.addChild(new DefaultConfiguration("TreeWalker"));

        Path cachePath = tempDir.resolve("nested").resolve("checkstyle-cache.properties");
        PropertyCacheFile cacheFile = new PropertyCacheFile(
            configuration, cachePath.toString());

        cacheFile.load();
        cacheFile.put("src/main/java/Example.java", 123L);
        cacheFile.persist();

        assertThat(cacheFile.isInCache("src/main/java/Example.java", 123L)).isTrue();
        assertThat(cacheFile.get("src/main/java/Example.java")).isEqualTo("123");

        Properties storedProperties = new Properties();
        try (InputStream inputStream = Files.newInputStream(cachePath)) {
            storedProperties.load(inputStream);
        }
        assertThat(storedProperties.getProperty(PropertyCacheFile.CONFIG_HASH_KEY)).isNotBlank();
        assertThat(storedProperties.getProperty("src/main/java/Example.java"))
            .isEqualTo("123");

        PropertyCacheFile reloadedCacheFile = new PropertyCacheFile(
            configuration, cachePath.toString());

        reloadedCacheFile.load();

        assertThat(reloadedCacheFile.isInCache("src/main/java/Example.java", 123L)).isTrue();
    }
}
