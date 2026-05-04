/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_activation.jakarta_activation_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import javax.activation.ActivationDataFlavor;
import javax.activation.CommandInfo;
import javax.activation.CommandMap;
import javax.activation.DataContentHandler;
import javax.activation.DataContentHandlerFactory;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.activation.FileTypeMap;
import javax.activation.MimeType;
import javax.activation.MimeTypeParameterList;
import javax.activation.MimeTypeParseException;
import javax.activation.URLDataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Jakarta_activation_apiTest {
    private static final String SAMPLE_TEXT = "activation sample";

    @Test
    void activationDataFlavorMatchesBaseMimeTypeAndRepresentationClass() throws Exception {
        ActivationDataFlavor flavor = new ActivationDataFlavor(String.class, "Text/Plain; charset=UTF-8", "Plain text");

        assertThat(flavor.getRepresentationClass()).isEqualTo(String.class);
        assertThat(flavor.getHumanPresentableName()).isEqualTo("Plain text");
        assertThat(flavor.getMimeType()).isEqualTo("Text/Plain; charset=UTF-8");
        assertThat(flavor.isMimeTypeEqual("text/plain; charset=US-ASCII")).isTrue();
        assertThat(flavor.equals(new DataFlavor("text/plain; class=java.lang.String"))).isTrue();

        flavor.setHumanPresentableName("Updated text");

        assertThat(flavor.getHumanPresentableName()).isEqualTo("Updated text");
    }

    @Test
    void mimeTypeParsesMatchesAndMutatesParameters() throws Exception {
        MimeType mimeType = new MimeType("Text/Plain; charset=\"UTF-8\"; format=flowed");

        assertThat(mimeType.getPrimaryType()).isEqualTo("text");
        assertThat(mimeType.getSubType()).isEqualTo("plain");
        assertThat(mimeType.getBaseType()).isEqualTo("text/plain");
        assertThat(mimeType.getParameter("CHARSET")).isEqualTo("UTF-8");
        assertThat(mimeType.getParameter("format")).isEqualTo("flowed");
        assertThat(mimeType.match("text/*")).isTrue();
        assertThat(mimeType.match(new MimeType("text/plain; charset=ISO-8859-1"))).isTrue();

        mimeType.setPrimaryType("Application");
        mimeType.setSubType("X-Test");
        mimeType.setParameter("version", "1");
        mimeType.removeParameter("format");

        assertThat(mimeType.getBaseType()).isEqualTo("application/x-test");
        assertThat(mimeType.getParameter("version")).isEqualTo("1");
        assertThat(mimeType.getParameter("format")).isNull();
        assertThat(mimeType.toString()).contains("application/x-test", "charset=UTF-8", "version=1");
        assertThatThrownBy(() -> new MimeType("missing-subtype"))
                .isInstanceOf(MimeTypeParseException.class);
    }

    @Test
    void mimeTypeParameterListHandlesQuotedValuesAndEnumeration() throws Exception {
        MimeTypeParameterList parameters = new MimeTypeParameterList("; charset=\"UTF-8\"; name=\"a b.txt\"");

        assertThat(parameters.size()).isEqualTo(2);
        assertThat(parameters.isEmpty()).isFalse();
        assertThat(parameters.get("CHARSET")).isEqualTo("UTF-8");
        assertThat(parameters.get("name")).isEqualTo("a b.txt");

        parameters.set("format", "flowed");
        parameters.remove("name");

        assertThat(parameters.get("format")).isEqualTo("flowed");
        assertThat(parameters.get("name")).isNull();
        assertThat(parameterNames(parameters)).containsExactlyInAnyOrder("charset", "format");
        assertThat(parameters.toString()).contains("charset=UTF-8", "format=flowed");
        assertThatThrownBy(() -> new MimeTypeParameterList("; broken"))
                .isInstanceOf(MimeTypeParseException.class);
    }

    @Test
    void dataHandlerUsesRegisteredDataContentHandlerFactory() throws Exception {
        DataContentHandlerFactory factory = mimeType -> {
            if ("application/x-factory-upper".equals(mimeType)) {
                return new UppercaseTextContentHandler("application/x-factory-upper");
            }
            return null;
        };
        DataHandler.setDataContentHandlerFactory(factory);
        DataHandler handler = new DataHandler("factory value", "application/x-factory-upper; charset=UTF-8");
        DataFlavor flavor = handler.getTransferDataFlavors()[0];

        assertThat(handler.isDataFlavorSupported(flavor)).isTrue();
        assertThat(handler.getContent()).isEqualTo("factory value");

        ByteArrayOutputStream directOutput = new ByteArrayOutputStream();
        handler.writeTo(directOutput);
        assertThat(directOutput.toString(StandardCharsets.UTF_8)).isEqualTo("FACTORY VALUE");

        try (InputStream inputStream = handler.getInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("FACTORY VALUE");
        }
    }

    @Test
    void fileDataSourceReadsWritesAndUsesAssignedFileTypeMap(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("payload.custom");
        FileDataSource dataSource = new FileDataSource(file.toFile());
        dataSource.setFileTypeMap(new FixedFileTypeMap("application/x-custom"));

        try (OutputStream outputStream = dataSource.getOutputStream()) {
            outputStream.write(SAMPLE_TEXT.getBytes(StandardCharsets.UTF_8));
        }

        assertThat(dataSource.getName()).isEqualTo("payload.custom");
        assertThat(dataSource.getFile()).isEqualTo(file.toFile());
        assertThat(dataSource.getContentType()).isEqualTo("application/x-custom");
        try (InputStream inputStream = dataSource.getInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(SAMPLE_TEXT);
        }
    }

    @Test
    void urlDataSourceAndUrlBackedDataHandlerExposeUrlContent(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("url-source.txt");
        Files.writeString(file, SAMPLE_TEXT, StandardCharsets.UTF_8);
        URL url = file.toUri().toURL();
        URLDataSource dataSource = new URLDataSource(url);

        assertThat(dataSource.getURL()).isEqualTo(url);
        assertThat(dataSource.getName()).endsWith("url-source.txt");
        try (InputStream inputStream = dataSource.getInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(SAMPLE_TEXT);
        }
        assertThatThrownBy(dataSource::getOutputStream).isInstanceOf(IOException.class);

        DataHandler handler = new DataHandler(url);

        assertThat(handler.getDataSource()).isInstanceOf(URLDataSource.class);
        try (InputStream inputStream = handler.getInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(SAMPLE_TEXT);
        }
    }

    @Test
    void dataHandlerWritesToWritableDataSourceOutputStream() throws Exception {
        WritableByteArrayDataSource dataSource = new WritableByteArrayDataSource("text/plain", "writable.txt");
        DataHandler handler = new DataHandler(dataSource);

        try (OutputStream outputStream = handler.getOutputStream()) {
            outputStream.write(SAMPLE_TEXT.getBytes(StandardCharsets.UTF_8));
        }

        assertThat(handler.getName()).isEqualTo("writable.txt");
        assertThat(handler.getContentType()).isEqualTo("text/plain");
        try (InputStream inputStream = handler.getInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(SAMPLE_TEXT);
        }
    }

    @Test
    void dataHandlerDelegatesDataSourceAccessAndCommandLookup() throws Exception {
        ByteArrayDataSource dataSource = new ByteArrayDataSource("bytes".getBytes(StandardCharsets.UTF_8),
                "application/x-bytes", "bytes.bin");
        DataHandler handler = new DataHandler(dataSource);
        CommandInfo view = new CommandInfo("view", "example.ViewCommand");
        CommandInfo edit = new CommandInfo("edit", "example.EditCommand");
        RecordingCommandMap commandMap = new RecordingCommandMap(new CommandInfo[] {view},
                new CommandInfo[] {view, edit}, null);
        handler.setCommandMap(commandMap);

        assertThat(handler.getDataSource()).isSameAs(dataSource);
        assertThat(handler.getName()).isEqualTo("bytes.bin");
        assertThat(handler.getContentType()).isEqualTo("application/x-bytes");
        try (InputStream inputStream = handler.getInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("bytes");
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        handler.writeTo(outputStream);
        assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo("bytes");
        assertThat(handler.getPreferredCommands()).containsExactly(view);
        assertThat(handler.getAllCommands()).containsExactly(view, edit);
        assertThat(handler.getCommand("edit")).isSameAs(edit);
        assertThat(commandMap.getRequestedMimeTypes()).containsExactly("application/x-bytes", "application/x-bytes",
                "application/x-bytes");
    }

    @Test
    void dataSourceBackedDataHandlerUsesDataContentHandlerFromCommandMap() throws Exception {
        ByteArrayDataSource dataSource = new ByteArrayDataSource("source value".getBytes(StandardCharsets.UTF_8),
                "application/x-upper", "source.txt");
        DataHandler handler = new DataHandler(dataSource);
        handler.setCommandMap(new RecordingCommandMap(new CommandInfo[0], new CommandInfo[0],
                new UppercaseTextContentHandler()));
        DataFlavor flavor = handler.getTransferDataFlavors()[0];

        assertThat(handler.isDataFlavorSupported(flavor)).isTrue();
        assertThat(handler.getTransferData(flavor)).isEqualTo("SOURCE VALUE");
        assertThat(handler.getContent()).isEqualTo("SOURCE VALUE");
    }

    @Test
    void objectBackedDataHandlerUsesDataContentHandlerFromCommandMap() throws Exception {
        UppercaseTextContentHandler contentHandler = new UppercaseTextContentHandler();
        DataHandler handler = new DataHandler("mixed Case", "application/x-upper; charset=UTF-8");
        handler.setCommandMap(new RecordingCommandMap(new CommandInfo[0], new CommandInfo[0], contentHandler));
        DataFlavor flavor = handler.getTransferDataFlavors()[0];

        assertThat(handler.getName()).isNull();
        assertThat(handler.getContentType()).isEqualTo("application/x-upper; charset=UTF-8");
        assertThat(handler.isDataFlavorSupported(flavor)).isTrue();
        assertThat(handler.getContent()).isEqualTo("mixed Case");

        ByteArrayOutputStream directOutput = new ByteArrayOutputStream();
        handler.writeTo(directOutput);
        assertThat(directOutput.toString(StandardCharsets.UTF_8)).isEqualTo("MIXED CASE");

        try (InputStream inputStream = handler.getInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("MIXED CASE");
        }
    }

    private static List<String> parameterNames(MimeTypeParameterList parameters) {
        Enumeration<?> names = parameters.getNames();
        ArrayList<String> result = new ArrayList<>();
        while (names.hasMoreElements()) {
            result.add((String) names.nextElement());
        }
        return result;
    }

    private static final class FixedFileTypeMap extends FileTypeMap {
        private final String contentType;

        private FixedFileTypeMap(String contentType) {
            this.contentType = contentType;
        }

        @Override
        public String getContentType(File file) {
            return contentType;
        }

        @Override
        public String getContentType(String filename) {
            return contentType;
        }
    }

    private static final class WritableByteArrayDataSource implements DataSource {
        private byte[] bytes = new byte[0];
        private final String contentType;
        private final String name;

        private WritableByteArrayDataSource(String contentType, String name) {
            this.contentType = contentType;
            this.name = name;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream() {
                @Override
                public void close() throws IOException {
                    super.close();
                    bytes = toByteArray();
                }
            };
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static final class ByteArrayDataSource implements DataSource {
        private final byte[] bytes;
        private final String contentType;
        private final String name;

        private ByteArrayDataSource(byte[] bytes, String contentType, String name) {
            this.bytes = bytes.clone();
            this.contentType = contentType;
            this.name = name;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            throw new IOException("read-only data source");
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static final class RecordingCommandMap extends CommandMap {
        private final CommandInfo[] preferredCommands;
        private final CommandInfo[] allCommands;
        private final DataContentHandler contentHandler;
        private final ArrayList<String> requestedMimeTypes = new ArrayList<>();

        private RecordingCommandMap(CommandInfo[] preferredCommands, CommandInfo[] allCommands,
                DataContentHandler contentHandler) {
            this.preferredCommands = preferredCommands.clone();
            this.allCommands = allCommands.clone();
            this.contentHandler = contentHandler;
        }

        @Override
        public CommandInfo[] getPreferredCommands(String mimeType) {
            requestedMimeTypes.add(mimeType);
            return preferredCommands.clone();
        }

        @Override
        public CommandInfo[] getAllCommands(String mimeType) {
            requestedMimeTypes.add(mimeType);
            return allCommands.clone();
        }

        @Override
        public CommandInfo getCommand(String mimeType, String commandName) {
            requestedMimeTypes.add(mimeType);
            return Arrays.stream(allCommands)
                    .filter(command -> command.getCommandName().equals(commandName))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public DataContentHandler createDataContentHandler(String mimeType) {
            requestedMimeTypes.add(mimeType);
            return contentHandler;
        }

        private List<String> getRequestedMimeTypes() {
            return requestedMimeTypes;
        }
    }

    private static final class UppercaseTextContentHandler implements DataContentHandler {
        private final ActivationDataFlavor flavor;

        private UppercaseTextContentHandler() {
            this("application/x-upper");
        }

        private UppercaseTextContentHandler(String mimeType) {
            this.flavor = new ActivationDataFlavor(String.class, mimeType, "Uppercase text");
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {flavor};
        }

        @Override
        public Object getTransferData(DataFlavor requestedFlavor, DataSource dataSource)
                throws UnsupportedFlavorException, IOException {
            if (!flavor.equals(requestedFlavor)) {
                throw new UnsupportedFlavorException(requestedFlavor);
            }
            return getContent(dataSource);
        }

        @Override
        public Object getContent(DataSource dataSource) throws IOException {
            try (InputStream inputStream = dataSource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
            }
        }

        @Override
        public void writeTo(Object object, String mimeType, OutputStream outputStream) throws IOException {
            outputStream.write(object.toString().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        }
    }
}
