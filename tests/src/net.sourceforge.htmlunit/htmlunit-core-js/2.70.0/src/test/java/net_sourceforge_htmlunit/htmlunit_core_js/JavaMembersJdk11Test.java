/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.NativeJavaObject;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.ScriptableObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaMembersJdk11Test {
    @Test
    void exposesMethodsFromNonExportedPathImplementationThroughPublicInterface() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Path path = Path.of("alpha", "bravo");
            NativeJavaObject wrapper = new NativeJavaObject(scope, path, Path.class);
            ScriptableObject.putProperty(scope, "path", wrapper);

            Object result =
                    cx.evaluateString(
                            scope,
                            "String(path.getFileName()) + ':' + path.isAbsolute();",
                            "java-members-jdk11-path-interface",
                            1,
                            null);

            assertThat(Context.toString(result)).isEqualTo("bravo:false");
        } finally {
            Context.exit();
        }
    }

    @Test
    void exposesMethodsFromNonExportedChannelImplementationThroughPublicSuperclass()
            throws IOException {
        Path tempFile = Files.createTempFile("htmlunit-core-js", ".tmp");
        try {
            try (FileChannel channel =
                    FileChannel.open(
                            tempFile, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                Context cx = Context.enter();
                try {
                    Scriptable scope = cx.initStandardObjects();
                    NativeJavaObject wrapper =
                            new NativeJavaObject(scope, channel, FileChannel.class);
                    ScriptableObject.putProperty(scope, "channel", wrapper);

                    Object result =
                            cx.evaluateString(
                                    scope,
                                    "channel.position();",
                                    "java-members-jdk11-channel-superclass",
                                    1,
                                    null);

                    assertThat(Context.toNumber(result)).isEqualTo(0.0);
                } finally {
                    Context.exit();
                }
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
