/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_observation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checked-exception observation API with stop-time key-value redaction: observeChecked,
 * observeCheckedWithContext, wrapChecked, scopedChecked and tryScopedChecked carrying IOExceptions,
 * ObservationFilters that remove and mask high-cardinality key values, and explicit
 * parentObservation linking.
 *
 * A CSV import job reads files that may be malformed and archives them to a store that may
 * refuse them; both failures are checked IOExceptions that the observation must record and
 * propagate unchanged. A redaction filter strips the file path and masks the customer e-mail
 * before the exporting handler sees the stopped observation.
 */
class CheckedImportJobObservationTest {

    private RecordingHandler handler;

    private ObservationRegistry registry;

    private CsvImporter importer;

    @BeforeEach
    void setUp() {
        handler = new RecordingHandler();
        registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(handler)
                .observationFilter(new RedactionFilter())
                .observationFilter(context -> context.addLowCardinalityKeyValue(KeyValue.of("job.kind", "import")));
        importer = new CsvImporter(registry);
    }

    @Test
    void importedFileIsObservedWithItsContextAndRedactedOnStop() throws IOException {
        int rows = importer.importFile("/data/in/customers.csv", "jane@example.com", """
                id,name
                1,Ada
                2,Grace
                3,Linus
                """);

        assertThat(rows).isEqualTo(3);
        assertThat(handler.log).containsExactly(
                "start:import.file",
                "scope-open:import.file",
                "scope-close:import.file",
                "stop:import.file");

        Map<String, String> atStart = handler.highCardinalityAtStart.get("import.file");
        assertThat(atStart)
                .containsEntry("file.path", "/data/in/customers.csv")
                .containsEntry("customer.email", "jane@example.com");

        ImportContext context = (ImportContext) handler.stopped.get(0);
        assertThat(context.rowsImported).isEqualTo(3);
        assertThat(context.getPath()).isEqualTo("/data/in/customers.csv");
        assertThat(context.getError()).isNull();
        assertThat(context.getHighCardinalityKeyValue("file.path")).isNull();
        assertThat(context.getHighCardinalityKeyValue("customer.email").getValue()).isEqualTo("j***@example.com");
        assertThat(context.getLowCardinalityKeyValue("format").getValue()).isEqualTo("csv");
        assertThat(context.getLowCardinalityKeyValue("job.kind").getValue()).isEqualTo("import");
        assertThat(context.getLowCardinalityKeyValue("outcome").getValue()).isEqualTo("imported");
        assertThat(context.getAllKeyValues().stream().map(KeyValue::getKey))
                .containsExactlyInAnyOrder("format", "job.kind", "outcome", "customer.email");
    }

    @Test
    void malformedRowPropagatesTheCheckedExceptionAndIsRecorded() {
        assertThatThrownBy(() -> importer.importFile("/data/in/broken.csv", "bob@example.org", """
                id,name
                1,Ada
                two
                """))
                .isInstanceOf(MalformedRowException.class)
                .hasMessage("line 3: expected 2 columns but found 1");

        assertThat(handler.log).containsExactly(
                "start:import.file",
                "scope-open:import.file",
                "scope-close:import.file",
                "error:import.file:MalformedRowException",
                "stop:import.file");

        ImportContext context = (ImportContext) handler.stopped.get(0);
        assertThat(context.getError()).isInstanceOf(MalformedRowException.class);
        assertThat(context.rowsImported).isEqualTo(1);
        assertThat(context.getLowCardinalityKeyValue("outcome")).isNull();
        assertThat(context.getHighCardinalityKeyValue("customer.email").getValue()).isEqualTo("b***@example.org");
        assertThat(context.getHighCardinalityKeyValue("file.path")).isNull();
    }

    @Test
    void wrappedArchiveTaskIsObservedOnlyWhenItRuns() throws IOException {
        ArchiveStore store = new ArchiveStore(2);

        Observation.CheckedRunnable<IOException> first = importer.archiveLater(store, "a.csv");
        Observation.CheckedRunnable<IOException> second = importer.archiveLater(store, "b.csv");
        Observation.CheckedRunnable<IOException> third = importer.archiveLater(store, "c.csv");
        assertThat(handler.log).isEmpty();

        first.run();
        second.run();
        assertThatThrownBy(third::run).isInstanceOf(IOException.class).hasMessage("archive full, refused c.csv");

        assertThat(store.archived).containsExactly("a.csv", "b.csv");
        assertThat(handler.log).containsExactly(
                "start:import.archive",
                "scope-open:import.archive",
                "scope-close:import.archive",
                "stop:import.archive",
                "start:import.archive",
                "scope-open:import.archive",
                "scope-close:import.archive",
                "stop:import.archive",
                "start:import.archive",
                "scope-open:import.archive",
                "scope-close:import.archive",
                "error:import.archive:IOException",
                "stop:import.archive");
        assertThat(handler.stopped).extracting(context -> context.getLowCardinalityKeyValue("job.kind").getValue())
                .containsOnly("import");
    }

    @Test
    void batchScopesItsFilesAndLinksTheLaterNotificationExplicitly() throws IOException {
        Observation batch = Observation.start("import.batch", registry);
        int total = batch.scopedChecked(() -> importer.importFile("/data/in/a.csv", "amy@example.com", """
                id,name
                1,Ada
                """) + importer.importFile("/data/in/b.csv", "ben@example.com", """
                id,name
                2,Grace
                3,Linus
                """));
        assertThat(total).isEqualTo(3);
        assertThat(registry.getCurrentObservation()).isNull();

        Observation.createNotStarted("import.notify", registry)
                .parentObservation(batch)
                .lowCardinalityKeyValue("channel", "email")
                .observeChecked(() -> {
                    assertThat(registry.getCurrentObservation().getContextView().getName()).isEqualTo("import.notify");
                });

        String unscoped = Observation.tryScopedChecked(null, () -> {
            assertThat(registry.getCurrentObservation()).isNull();
            return "ran without an observation";
        });
        assertThat(unscoped).isEqualTo("ran without an observation");
        batch.stop();

        assertThat(handler.stopped).extracting(Observation.Context::getName)
                .containsExactly("import.file", "import.file", "import.notify", "import.batch");
        for (Observation.Context context : handler.stopped.subList(0, 3)) {
            assertThat(context.getParentObservation()).isSameAs(batch);
        }
        assertThat(handler.stopped.get(3).getParentObservation()).isNull();
        assertThat(handler.stopped.get(2).getLowCardinalityKeyValue("channel").getValue()).isEqualTo("email");
    }

    /** Application service that imports CSV files and archives them, both throwing IOException. */
    static final class CsvImporter {

        private final ObservationRegistry registry;

        CsvImporter(ObservationRegistry registry) {
            this.registry = registry;
        }

        int importFile(String path, String customerEmail, String content) throws IOException {
            return Observation.createNotStarted("import.file", () -> new ImportContext(path), registry)
                    .lowCardinalityKeyValue("format", "csv")
                    .highCardinalityKeyValue("file.path", path)
                    .highCardinalityKeyValue("customer.email", customerEmail)
                    .observeCheckedWithContext((ImportContext context) -> {
                        int rows = parse(context, content);
                        context.addLowCardinalityKeyValue(KeyValue.of("outcome", "imported"));
                        return rows;
                    });
        }

        Observation.CheckedRunnable<IOException> archiveLater(ArchiveStore store, String file) throws IOException {
            return Observation.createNotStarted("import.archive", registry)
                    .highCardinalityKeyValue("file.path", file)
                    .wrapChecked(() -> store.archive(file));
        }

        private static int parse(ImportContext context, String content) throws IOException {
            try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
                String header = reader.readLine();
                int columns = header.split(",").length;
                int lineNumber = 1;
                String line;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    int found = line.split(",").length;
                    if (found != columns) {
                        throw new MalformedRowException(
                                "line " + lineNumber + ": expected " + columns + " columns but found " + found);
                    }
                    context.rowsImported++;
                }
            }
            return context.rowsImported;
        }
    }

    /** Context carrying the job's own state alongside the observation's key values. */
    static final class ImportContext extends Observation.Context {

        private final String path;

        private int rowsImported;

        ImportContext(String path) {
            this.path = path;
        }

        String getPath() {
            return path;
        }
    }

    /** Checked failure raised for a row that does not match the header. */
    static final class MalformedRowException extends IOException {

        MalformedRowException(String message) {
            super(message);
        }
    }

    /** Archive with a fixed capacity that refuses files once it is full. */
    static final class ArchiveStore {

        private final int capacity;

        private final List<String> archived = new ArrayList<>();

        ArchiveStore(int capacity) {
            this.capacity = capacity;
        }

        void archive(String file) throws IOException {
            if (archived.size() == capacity) {
                throw new IOException("archive full, refused " + file);
            }
            archived.add(file);
        }
    }

    /** Strips file paths and masks customer e-mails before an observation is exported. */
    static final class RedactionFilter implements ObservationFilter {

        @Override
        public Observation.Context map(Observation.Context context) {
            KeyValue email = context.getHighCardinalityKeyValue("customer.email");
            context.removeHighCardinalityKeyValues("file.path", "customer.email");
            if (email != null) {
                String value = email.getValue();
                int at = value.indexOf('@');
                context.addHighCardinalityKeyValue(
                        KeyValue.of("customer.email", value.charAt(0) + "***" + value.substring(at)));
            }
            return context;
        }
    }

    /** Exporting backend that records the lifecycle and the key values it observed at each stage. */
    static final class RecordingHandler implements ObservationHandler<Observation.Context> {

        private final List<String> log = new ArrayList<>();

        private final List<Observation.Context> stopped = new ArrayList<>();

        private final Map<String, Map<String, String>> highCardinalityAtStart = new LinkedHashMap<>();

        @Override
        public void onStart(Observation.Context context) {
            log.add("start:" + context.getName());
            Map<String, String> snapshot = new LinkedHashMap<>();
            context.getHighCardinalityKeyValues().forEach(keyValue -> snapshot.put(keyValue.getKey(), keyValue.getValue()));
            highCardinalityAtStart.put(context.getName(), snapshot);
        }

        @Override
        public void onError(Observation.Context context) {
            log.add("error:" + context.getName() + ":" + context.getError().getClass().getSimpleName());
        }

        @Override
        public void onScopeOpened(Observation.Context context) {
            log.add("scope-open:" + context.getName());
        }

        @Override
        public void onScopeClosed(Observation.Context context) {
            log.add("scope-close:" + context.getName());
        }

        @Override
        public void onStop(Observation.Context context) {
            log.add("stop:" + context.getName());
            stopped.add(context);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }
}
