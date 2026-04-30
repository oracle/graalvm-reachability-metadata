/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.diffplug.spotless.Formatter;
import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.json.JsonPatchStep;

public class JsonPatchStepInnerStateTest {
    private static final String INPUT = "{\"name\":\"old\"}";
    private static final String PATCH_STRING = "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"new\"}]";

    private final Provisioner provisioner = new FixtureProvisioner();

    @Test
    void createsJsonPatchFormatterFromPatchString() throws Exception {
        final FormatterStep step = JsonPatchStep.create(PATCH_STRING, provisioner);

        assertThat(step.format(INPUT, Formatter.NO_FILE_SENTINEL))
                .isEqualTo("patch-string:" + PATCH_STRING + ":" + INPUT);
    }

    @Test
    void createsJsonPatchFormatterFromPatchList() throws Exception {
        final List<Map<String, Object>> patch = List.of(replaceNameOperation());
        final FormatterStep step = JsonPatchStep.create(patch, provisioner);

        assertThat(step.format(INPUT, Formatter.NO_FILE_SENTINEL)).isEqualTo("patch-list:" + patch + ":" + INPUT);
    }

    private static Map<String, Object> replaceNameOperation() {
        final Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("op", "replace");
        operation.put("path", "/name");
        operation.put("value", "new");
        return operation;
    }

    private static final class FixtureProvisioner implements Provisioner {
        @Override
        public Set<File> provisionWithTransitives(boolean withTransitives, Collection<String> mavenCoordinates) {
            return extractFixtureJars();
        }

        private static Set<File> extractFixtureJars() {
            try {
                final Set<File> files = new LinkedHashSet<>();
                try (InputStream inputStream = resource("json-patch-fixtures/current.classpath");
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String jarResource;
                    while ((jarResource = reader.readLine()) != null) {
                        if (!jarResource.isBlank()) {
                            files.add(extractResource(jarResource).toFile());
                        }
                    }
                }
                return files;
            } catch (IOException e) {
                throw new IllegalStateException("Unable to extract JSON patch fixture jars", e);
            }
        }

        private static Path extractResource(String resourceName) throws IOException {
            final Path resourcePath = Path.of(resourceName);
            final Path targetDirectory = Files.createTempDirectory("json-patch-fixture-");
            final Path target = targetDirectory.resolve(resourcePath.getFileName().toString());
            try (InputStream inputStream = resource(resourceName)) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            target.toFile().deleteOnExit();
            targetDirectory.toFile().deleteOnExit();
            return target;
        }

        private static InputStream resource(String resourceName) {
            final InputStream inputStream = JsonPatchStepInnerStateTest.class.getClassLoader()
                    .getResourceAsStream(resourceName);
            if (inputStream == null) {
                throw new IllegalArgumentException("Missing test resource: " + resourceName);
            }
            return inputStream;
        }
    }
}
