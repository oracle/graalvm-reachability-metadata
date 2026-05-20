/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_activation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import com.sun.activation.registries.MailcapFile;
import com.sun.activation.registries.MailcapRegistryProviderImpl;
import com.sun.activation.registries.MailcapTokenizer;
import com.sun.activation.registries.MimeTypeFile;
import com.sun.activation.registries.MimeTypeRegistryProviderImpl;
import jakarta.activation.MailcapRegistry;
import jakarta.activation.MimeTypeEntry;
import jakarta.activation.MimeTypeRegistry;
import jakarta.activation.spi.MailcapRegistryProvider;
import jakarta.activation.spi.MimeTypeRegistryProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class Angus_activationTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void mimeTypeFileParsesLegacyAndStructuredEntriesFromStream() throws IOException {
        MimeTypeFile registry = new MimeTypeFile(streamOf("""
                # comments and empty lines are ignored

                text/plain txt text
                application/xml xml xsl
                type=application/json desc="JSON document" exts=json,map
                type=image/svg+xml desc="Scalable Vector Graphics" exts=svg,svgz
                """));

        assertThat(registry.getMIMETypeString("txt")).isEqualTo("text/plain");
        assertThat(registry.getMIMETypeString("text")).isEqualTo("text/plain");
        assertThat(registry.getMIMETypeString("xml")).isEqualTo("application/xml");
        assertThat(registry.getMIMETypeString("json")).isEqualTo("application/json");
        assertThat(registry.getMIMETypeString("map")).isEqualTo("application/json");
        assertThat(registry.getMIMETypeString("svgz")).isEqualTo("image/svg+xml");
        assertThat(registry.getMIMETypeString("missing")).isNull();

        MimeTypeEntry svgEntry = registry.getMimeTypeEntry("svg");
        assertThat(svgEntry.getMIMEType()).isEqualTo("image/svg+xml");
        assertThat(svgEntry.getFileExtension()).isEqualTo("svg");
        assertThat(svgEntry.toString()).contains("image/svg+xml", "svg");
    }

    @Test
    void mimeTypeRegistrySupportsAppendFileAndProviderConstruction() throws IOException {
        Path mimeTypesFile = temporaryDirectory.resolve("mime.types");
        Files.writeString(mimeTypesFile, "application/pdf pdf\n", StandardCharsets.ISO_8859_1);

        MimeTypeRegistryProvider provider = new MimeTypeRegistryProviderImpl();
        MimeTypeRegistry fromFile = provider.getByFileName(mimeTypesFile.toString());
        MimeTypeRegistry fromStream = provider.getByInputStream(streamOf("image/png png\n"));
        MimeTypeRegistry inMemory = provider.getInMemory();

        inMemory.appendToRegistry("""
                # old format with a continued line
                application/vnd.example alpha \\
                    beta
                type=application/x-example desc="quoted description" exts=ex1,ex2
                """);

        assertThat(fromFile.getMIMETypeString("pdf")).isEqualTo("application/pdf");
        assertThat(fromStream.getMIMETypeString("png")).isEqualTo("image/png");
        assertThat(inMemory.getMIMETypeString("alpha")).isEqualTo("application/vnd.example");
        assertThat(inMemory.getMIMETypeString("beta")).isEqualTo("application/vnd.example");
        assertThat(inMemory.getMIMETypeString("ex2")).isEqualTo("application/x-example");
    }

    @Test
    void mailcapFileParsesCommandsFallbacksWildcardsAndNativeCommands() throws IOException {
        MailcapFile mailcap = new MailcapFile(streamOf("""
                # normal text handlers
                text/plain; native-view %s; x-java-view=com.example.Viewer; x-java-edit=com.example.Editor
                text/*; wildcard-view %s; x-java-view=com.example.TextWildcardViewer; x-java-print=com.example.Printer
                text/plain; another-view %s; x-java-view=com.example.SecondViewer
                image/*; fallback-view %s; x-java-fallback-entry=true; x-java-view=com.example.FallbackViewer
                """));

        Map<?, ?> plainTextCommands = mailcap.getMailcapList("text/plain");
        assertThat(commandClasses(plainTextCommands, "view"))
                .containsExactly("com.example.Viewer", "com.example.SecondViewer", "com.example.TextWildcardViewer");
        assertThat(commandClasses(plainTextCommands, "edit")).containsExactly("com.example.Editor");
        assertThat(commandClasses(plainTextCommands, "print")).containsExactly("com.example.Printer");

        Map<?, ?> htmlCommands = mailcap.getMailcapList("text/html");
        assertThat(commandClasses(htmlCommands, "view")).containsExactly("com.example.TextWildcardViewer");
        assertThat(commandClasses(htmlCommands, "print")).containsExactly("com.example.Printer");

        Map<?, ?> imageFallbackCommands = mailcap.getMailcapFallbackList("image/png");
        assertThat(commandClasses(imageFallbackCommands, "view")).containsExactly("com.example.FallbackViewer");
        assertThat(mailcap.getMailcapFallbackList("text/plain")).isNull();

        assertThat(mailcap.getMimeTypes()).contains("text/plain", "text/*", "image/*");
        assertThat(mailcap.getNativeCommands("TEXT/PLAIN"))
                .containsExactly(
                        "text/plain; native-view %s; x-java-view=com.example.Viewer; x-java-edit=com.example.Editor",
                        "text/plain; another-view %s; x-java-view=com.example.SecondViewer");
        assertThat(mailcap.getNativeCommands("application/json")).isNull();
    }

    @Test
    void mailcapFileDeduplicatesCommandClassesWhenMergingEntries() throws IOException {
        MailcapFile mailcap = new MailcapFile(streamOf("""
                application/x-merge; first-view %s; \
                    x-java-view=com.example.PrimaryViewer; x-java-edit=com.example.Editor
                application/x-merge; second-view %s; \
                    x-java-view=com.example.PrimaryViewer; x-java-view=com.example.SecondaryViewer; \
                    x-java-edit=com.example.Editor
                """));

        Map<?, ?> commands = mailcap.getMailcapList("application/x-merge");

        assertThat(commandClasses(commands, "view"))
                .containsExactly("com.example.PrimaryViewer", "com.example.SecondaryViewer");
        assertThat(commandClasses(commands, "edit")).containsExactly("com.example.Editor");
    }

    @Test
    void mailcapFileSupportsJavaOnlyEntriesAndPrimaryTypeShorthand() throws IOException {
        MailcapFile mailcap = new MailcapFile(streamOf("""
                APPLICATION/X-JAVA-ONLY;; X-JAVA-VIEW=com.example.JavaOnlyViewer
                text;; x-java-print=com.example.TextPrinter
                """));

        assertThat(commandClasses(mailcap.getMailcapList("application/x-java-only"), "view"))
                .containsExactly("com.example.JavaOnlyViewer");
        assertThat(mailcap.getNativeCommands("application/x-java-only")).isNull();

        assertThat(commandClasses(mailcap.getMailcapList("text/plain"), "print"))
                .containsExactly("com.example.TextPrinter");
        assertThat(commandClasses(mailcap.getMailcapList("text/*"), "print"))
                .containsExactly("com.example.TextPrinter");
        assertThat(mailcap.getMimeTypes()).contains("application/x-java-only", "text/*");
    }

    @Test
    void mailcapRegistrySupportsAppendFileAndProviderConstruction() throws IOException {
        Path mailcapFile = temporaryDirectory.resolve("mailcap");
        Files.writeString(
                mailcapFile,
                "application/pdf; pdf-view %s; x-java-view=com.example.PdfViewer\n",
                StandardCharsets.ISO_8859_1);

        MailcapRegistryProvider provider = new MailcapRegistryProviderImpl();
        MailcapRegistry fromFile = provider.getByFileName(mailcapFile.toString());
        MailcapRegistry fromStream = provider.getByInputStream(streamOf(
                "image/png; png-view %s; x-java-view=com.example.PngViewer\n"));
        MailcapRegistry inMemory = provider.getInMemory();

        inMemory.appendToMailcap("""
                application/x-demo; demo-view %s; \\
                    x-java-view=com.example.DemoViewer; x-java-content-handler=com.example.DemoHandler
                malformed entry that should be ignored
                """);

        assertThat(commandClasses(fromFile.getMailcapList("application/pdf"), "view"))
                .containsExactly("com.example.PdfViewer");
        assertThat(commandClasses(fromStream.getMailcapList("image/png"), "view"))
                .containsExactly("com.example.PngViewer");
        assertThat(commandClasses(inMemory.getMailcapList("application/x-demo"), "view"))
                .containsExactly("com.example.DemoViewer");
        assertThat(commandClasses(inMemory.getMailcapList("application/x-demo"), "content-handler"))
                .containsExactly("com.example.DemoHandler");
    }

    @Test
    void mailcapTokenizerReportsTokenTypesAndAutoquotedValues() {
        MailcapTokenizer tokenizer = new MailcapTokenizer("text/plain; x-java-view=com.example.Viewer; @");

        assertThat(tokenizer.getCurrentToken()).isEqualTo(MailcapTokenizer.START_TOKEN);
        assertThat(MailcapTokenizer.nameForToken(tokenizer.getCurrentToken())).isEqualTo("start");
        assertToken(tokenizer, MailcapTokenizer.STRING_TOKEN, "text");
        assertToken(tokenizer, MailcapTokenizer.SLASH_TOKEN, "/");
        assertToken(tokenizer, MailcapTokenizer.STRING_TOKEN, "plain");
        assertToken(tokenizer, MailcapTokenizer.SEMICOLON_TOKEN, ";");
        assertToken(tokenizer, MailcapTokenizer.STRING_TOKEN, "x-java-view");
        assertToken(tokenizer, MailcapTokenizer.EQUALS_TOKEN, "=");

        tokenizer.setIsAutoquoting(true);
        assertToken(tokenizer, MailcapTokenizer.STRING_TOKEN, "com.example.Viewer");
        assertToken(tokenizer, MailcapTokenizer.SEMICOLON_TOKEN, ";");
        tokenizer.setIsAutoquoting(false);
        assertToken(tokenizer, MailcapTokenizer.UNKNOWN_TOKEN, "@");
        assertToken(tokenizer, MailcapTokenizer.EOI_TOKEN, null);

        assertThat(MailcapTokenizer.nameForToken(MailcapTokenizer.UNKNOWN_TOKEN)).isEqualTo("unknown");
        assertThat(MailcapTokenizer.nameForToken(MailcapTokenizer.EOI_TOKEN)).isEqualTo("EOI");
        assertThat(MailcapTokenizer.nameForToken('*')).isEqualTo("really unknown");
    }

    @Test
    void serviceLoaderDiscoversAngusRegistryProviders() {
        List<MailcapRegistryProvider> mailcapProviders = ServiceLoader.load(MailcapRegistryProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        List<MimeTypeRegistryProvider> mimeTypeProviders = ServiceLoader.load(MimeTypeRegistryProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertThat(mailcapProviders.stream().map(Object::getClass).toList())
                .contains(MailcapRegistryProviderImpl.class);
        assertThat(mimeTypeProviders.stream().map(Object::getClass).toList())
                .contains(MimeTypeRegistryProviderImpl.class);
    }

    private static ByteArrayInputStream streamOf(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void assertToken(MailcapTokenizer tokenizer, int expectedToken, String expectedValue) {
        assertThat(tokenizer.nextToken()).isEqualTo(expectedToken);
        assertThat(tokenizer.getCurrentTokenValue()).isEqualTo(expectedValue);
        assertThat(tokenizer.getCurrentToken()).isEqualTo(expectedToken);
    }

    @SuppressWarnings("unchecked")
    private static List<String> commandClasses(Map<?, ?> commands, String commandName) {
        assertThat(commands.containsKey(commandName)).isTrue();
        return (List<String>) commands.get(commandName);
    }
}
