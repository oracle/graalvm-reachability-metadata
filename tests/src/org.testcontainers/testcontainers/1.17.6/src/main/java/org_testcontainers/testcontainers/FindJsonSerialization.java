/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import com.github.dockerjava.api.model.DockerObject;
import org.reflections.Reflections;
import org.testcontainers.shaded.com.github.dockerjava.core.command.AbstrDockerCmd;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.reflections.scanners.Scanners.SubTypes;

class FindJsonSerialization {
    public static void main(String[] args) throws IOException {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off");

        printClasses(new Reflections("org.testcontainers"), AbstrDockerCmd.class, "/DockerCommand-template.txt");
        printClasses(new Reflections("com.github.dockerjava.api"), DockerObject.class, "/DockerObject-template.txt");
    }

    private static void printClasses(Reflections reflections, Class<?> baseClass, String template) throws IOException {
        Set<Class<?>> subTypes = reflections.get(SubTypes.of(baseClass).asClass());
        String loadedTemplate = loadTemplate(template);

        for (Class<?> subType : subTypes) {
            if (!Modifier.isPublic(subType.getModifiers()) || subType.isInterface() || Modifier.isAbstract(subType.getModifiers())) {
                continue;
            }
            System.out.printf(loadedTemplate, subType.getName());
        }
    }

    private static String loadTemplate(String resource) throws IOException {
        try (InputStream stream = FindJsonSerialization.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException(String.format("Resource '%s' not found", resource));
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
