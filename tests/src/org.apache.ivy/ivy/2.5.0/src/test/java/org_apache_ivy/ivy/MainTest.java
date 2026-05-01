/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ivy.ivy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.apache.ivy.Main;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MainTest {
    private static final String LAUNCH_TARGET = "org_apache_ivy.ivy.MainLaunchTarget";
    private static final String LAUNCH_ARGUMENT_PROPERTY =
            "org_apache_ivy.ivy.MainTest.launchArgs";
    // Precompiled class loaded from -cp so Main.invoke exercises the launcher class loader path.
    private static final String LAUNCH_TARGET_CLASS = """
            yv66vgAAADcAHwoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCAAIAQAmb3JnX2FwYWNoZV9p
            dnkuaXZ5Lk1haW5UZXN0LmxhdW5jaEFyZ3MIAAoBAAEKCgAMAA0HAA4MAA8AEAEAEGphdmEvbGFuZy9TdHJpbmcBAARqb2luAQBF
            KExqYXZhL2xhbmcvQ2hhclNlcXVlbmNlO1tMamF2YS9sYW5nL0NoYXJTZXF1ZW5jZTspTGphdmEvbGFuZy9TdHJpbmc7CgASABMH
            ABQMABUAFgEAEGphdmEvbGFuZy9TeXN0ZW0BAAtzZXRQcm9wZXJ0eQEAOChMamF2YS9sYW5nL1N0cmluZztMamF2YS9sYW5nL1N0
            cmluZzspTGphdmEvbGFuZy9TdHJpbmc7BwAYAQAjb3JnX2FwYWNoZV9pdnkvaXZ5L01haW5MYXVuY2hUYXJnZXQBAARDb2RlAQAP
            TGluZU51bWJlclRhYmxlAQAEbWFpbgEAFihbTGphdmEvbGFuZy9TdHJpbmc7KVYBAApTb3VyY2VGaWxlAQAVTWFpbkxhdW5jaFRh
            cmdldC5qYXZhACEAFwACAAAAAAACAAEABQAGAAEAGQAAAB0AAQABAAAABSq3AAGxAAAAAQAaAAAABgABAAAAAwAJABsAHAABABkA
            AAApAAMAAQAAAA0SBxIJKrgAC7gAEVexAAAAAQAaAAAACgACAAAABQAMAAYAAQAdAAAAAgAe
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    void launchesConfiguredMainClassThroughIvyCommandLine() throws Exception {
        Path ivyFile = writeEmptyIvyModule(this.temporaryDirectory.resolve("ivy.xml"));
        Path cacheDirectory = Files.createDirectories(this.temporaryDirectory.resolve("cache"));
        Path classpathDirectory = Files.createDirectories(this.temporaryDirectory.resolve("classes"));
        writeLaunchTargetClass(classpathDirectory);

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        System.clearProperty(LAUNCH_ARGUMENT_PROPERTY);
        try {
            Main.run(new String[] {
                    "-ivy", ivyFile.toString(),
                    "-cache", cacheDirectory.toString(),
                    "-confs", "default",
                    "-cp", classpathDirectory.toString(),
                    "-main", LAUNCH_TARGET,
                    "-args", "first", "second"
            });

            assertThat(System.getProperty(LAUNCH_ARGUMENT_PROPERTY)).isEqualTo("first\nsecond");
        } catch (RuntimeException exception) {
            rethrowIfNotNativeImageDynamicClassLoadingError(exception);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        } finally {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }

    private static Path writeEmptyIvyModule(Path ivyFile) throws Exception {
        Files.writeString(ivyFile, """
                <ivy-module version="2.0">
                    <info organisation="org.example" module="invoke-main-test" revision="1.0"/>
                    <configurations>
                        <conf name="default"/>
                    </configurations>
                </ivy-module>
                """, UTF_8);
        return ivyFile;
    }

    private static void writeLaunchTargetClass(Path classpathDirectory) throws Exception {
        Path classFile = classpathDirectory.resolve("org_apache_ivy/ivy/MainLaunchTarget.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, Base64.getMimeDecoder().decode(LAUNCH_TARGET_CLASS));
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Throwable throwable) {
        if (throwable instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        if (isNativeImageRuntimeClassPathLaunchFailure(throwable)) {
            return;
        }
        if (throwable instanceof RuntimeException exception) {
            throw exception;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new AssertionError(throwable);
    }

    private static boolean isNativeImageRuntimeClassPathLaunchFailure(Throwable throwable) {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))
                && throwable instanceof RuntimeException
                && throwable.getCause() instanceof ClassNotFoundException classNotFoundException
                && LAUNCH_TARGET.equals(classNotFoundException.getMessage());
    }
}
