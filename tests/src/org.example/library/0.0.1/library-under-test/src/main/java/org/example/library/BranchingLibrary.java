/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.example.library;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BranchingLibrary {

    private static final String DISABLE_REFLECTION_BRANCH_PROPERTY = "org.example.library.disableReflectionBranch";
    private static final boolean REFLECTION_BRANCH_ENABLED = !Boolean.getBoolean(DISABLE_REFLECTION_BRANCH_PROPERTY);
    private static final String REFLECTION_RESOURCE = "/org/example/library/messages/reflection-branch.txt";
    private static final String FALLBACK_RESOURCE = "/org/example/library/messages/fallback-branch.txt";

    private BranchingLibrary() {
    }

    public static BranchExecution run() {
        if (REFLECTION_BRANCH_ENABLED) {
            String reflectionPayload = runReflectionBranch();
            return new BranchExecution("reflection", reflectionPayload);
        }
        String fallbackPayload = runFallbackBranch();
        return new BranchExecution("fallback", fallbackPayload);
    }

    public static boolean isReflectionBranchEnabled() {
        return REFLECTION_BRANCH_ENABLED;
    }

    private static String runReflectionBranch() {
        Class<?> targetClass = loadClass("org.example.library.ReflectionTarget");
        declaredMethodCount(targetClass);
        Constructor<?> constructor = noArgConstructor(targetClass);
        Object target = instantiate(constructor);

        Method prefixMethod = method(targetClass, "prefix");
        String prefix = invokeString(target, prefixMethod);

        Method greetMethod = method(targetClass, "greet", String.class);
        String greeting = invokeString(target, greetMethod, "native");

        Field counterField = field(targetClass, "COUNTER");
        int counter = readStaticInt(counterField);

        List<String> resourceLines = loadResourceLines(REFLECTION_RESOURCE);
        String summary = summarizeResourceLines(resourceLines);
        return joinPayload(prefix, greeting, counter, summary);
    }

    private static String runFallbackBranch() {
        List<String> fallbackLines = loadResourceLines(FALLBACK_RESOURCE);
        List<String> normalizedFallback = normalizeLines(fallbackLines);
        return String.join(":", normalizedFallback);
    }

    private static int declaredMethodCount(Class<?> type) {
        return type.getDeclaredMethods().length;
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Could not load class " + className, exception);
        }
    }

    private static Constructor<?> noArgConstructor(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not resolve constructor for " + type.getName(), exception);
        }
    }

    private static Object instantiate(Constructor<?> constructor) {
        try {
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not instantiate " + constructor.getDeclaringClass().getName(), exception);
        }
    }

    private static Method method(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = type.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not resolve method " + methodName + " on " + type.getName(), exception);
        }
    }

    private static String invokeString(Object receiver, Method method, Object... args) {
        try {
            return (String) method.invoke(receiver, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not invoke " + method.getName(), exception);
        }
    }

    private static Field field(Class<?> type, String fieldName) {
        try {
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not resolve field " + fieldName + " on " + type.getName(), exception);
        }
    }

    private static int readStaticInt(Field field) {
        try {
            return field.getInt(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not read field " + field.getName(), exception);
        }
    }

    private static List<String> loadResourceLines(String path) {
        try (InputStream stream = openResource(path)) {
            List<String> lines = readRawLines(stream);
            return normalizeLines(lines);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read resource " + path, exception);
        }
    }

    private static boolean unusedResourceProbe(String path) {
        InputStream stream = BranchingLibrary.class.getResourceAsStream(path);
        if (stream == null) {
            return false;
        }
        try (InputStream ignored = stream) {
            return true;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not close resource " + path, exception);
        }
    }

    private static InputStream openResource(String path) {
        InputStream stream = BranchingLibrary.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("Missing resource " + path);
        }
        return stream;
    }

    private static List<String> readRawLines(InputStream stream) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while (line != null) {
                lines.add(line);
                line = reader.readLine();
            }
        }
        return lines;
    }

    private static List<String> normalizeLines(List<String> lines) {
        List<String> normalized = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private static String summarizeResourceLines(List<String> normalizedLines) {
        List<String> upperCaseLines = new ArrayList<>();
        for (String line : normalizedLines) {
            upperCaseLines.add(line.toUpperCase(Locale.ROOT));
        }
        return String.join("+", upperCaseLines);
    }

    private static String joinPayload(String prefix, String greeting, int counter, String summary) {
        return prefix + "|" + greeting + "|" + counter + "|" + summary;
    }

    public record BranchExecution(String branchName, String payload) {
    }
}
