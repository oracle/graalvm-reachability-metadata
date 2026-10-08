/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_orbisgis.h2gis_api;

import org.h2gis.api.DeterministicScalarFunction;
import org.h2gis.api.DriverFunction;
import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.api.FileDriver;
import org.h2gis.api.Function;
import org.h2gis.api.ProgressVisitor;
import org.h2gis.api.ScalarFunction;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class H2gis_apiTest {

    @Test
    void storesFunctionPropertiesAndExposesScalarFunctionContract() {
        TestScalarFunction function = new TestScalarFunction();

        assertThat(function).isInstanceOf(Function.class);
        assertThat(function).isInstanceOf(ScalarFunction.class);
        assertThat(function.getJavaStaticMethod()).isEqualTo("evaluate");
        assertThat(ScalarFunction.PROP_DETERMINISTIC).isNotBlank();

        function.addProperty(Function.PROP_NAME, "buffer");
        function.addProperty(Function.PROP_REMARKS, "Creates a geometry buffer");
        function.addProperty(ScalarFunction.PROP_DETERMINISTIC, true);

        assertThat(function.getProperty(Function.PROP_NAME)).isEqualTo("buffer");
        assertThat(function.getProperty(Function.PROP_REMARKS))
                .isEqualTo("Creates a geometry buffer");
        assertThat(function.getProperty(ScalarFunction.PROP_DETERMINISTIC)).isEqualTo(true);
        assertThat(function.removeProperty(Function.PROP_NAME)).isTrue();
        assertThat(function.getProperty(Function.PROP_NAME)).isNull();
        assertThat(function.removeProperty(Function.PROP_NAME)).isFalse();
    }

    @Test
    void marksDeterministicScalarFunctionsAsDeterministicByDefault() {
        TestScalarFunction function = new TestScalarFunction();

        assertThat(function.getProperty(ScalarFunction.PROP_DETERMINISTIC)).isEqualTo(true);
    }

    @Test
    void replacesAnExistingFunctionPropertyValue() {
        TestScalarFunction function = new TestScalarFunction();

        function.addProperty(Function.PROP_NAME, "initial");
        function.addProperty(Function.PROP_NAME, "updated");

        assertThat(function.getProperty(Function.PROP_NAME)).isEqualTo("updated");
        assertThat(function.removeProperty(Function.PROP_NAME)).isTrue();
        assertThat(function.getProperty(Function.PROP_NAME)).isNull();
    }

    @Test
    void reportsProgressVisitorStateAndCancellationEvents() {
        EmptyProgressVisitor visitor = new EmptyProgressVisitor();
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        visitor.addPropertyChangeListener(ProgressVisitor.PROPERTY_CANCELED, listener);

        assertThat(visitor.isCanceled()).isFalse();
        assertThat(visitor.getStepCount()).isZero();
        assertThat(visitor.getProgression()).isZero();
        assertThat(visitor.subProcess(3)).isSameAs(visitor);

        visitor.setStep(2);
        visitor.endStep();
        visitor.endOfProgress();
        visitor.cancel();
        visitor.cancel();

        assertThat(visitor.isCanceled()).isTrue();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getPropertyName()).isEqualTo(ProgressVisitor.PROPERTY_CANCELED);
            assertThat(event.getOldValue()).isEqualTo(false);
            assertThat(event.getNewValue()).isEqualTo(true);
        });

        visitor.removePropertyChangeListener(listener);
        visitor.cancel();
        assertThat(events).hasSize(1);
    }

    @Test
    void implementsDriverFunctionFormatAndImportContracts() throws Exception {
        TestDriver driver = new TestDriver();

        assertThat(driver.getImportDriverType()).isEqualTo(DriverFunction.IMPORT_DRIVER_TYPE.COPY);
        assertThat(driver.getImportFormats()).containsExactly("csv", "geojson");
        assertThat(driver.getExportFormats()).containsExactly("geojson");
        assertThat(driver.getFormatDescription("geojson")).isEqualTo("GeoJSON");
        assertThat(driver.isSpatialFormat("geojson")).isTrue();
        assertThat(driver.isSpatialFormat("csv")).isFalse();

        File source = new File("source.geojson");
        File destination = new File("destination.geojson");
        ProgressVisitor visitor = new EmptyProgressVisitor();

        assertThat(driver.importFile(null, "places", source, visitor))
                .containsExactly("places");
        assertThat(driver.importFile(null, "places", source, "EPSG:4326", visitor))
                .containsExactly("places");
        assertThat(driver.importFile(null, "places", source, true, visitor))
                .containsExactly("places");
        assertThat(driver.importFile(null, "places", source, "EPSG:4326", true, visitor))
                .containsExactly("places");
        assertThat(driver.exportTable(null, "places", destination, visitor))
                .containsExactly("places");
        assertThat(driver.exportTable(null, "places", destination, true, visitor))
                .containsExactly("places");
        assertThat(driver.exportTable(null, "places", destination, "EPSG:4326", visitor))
                .containsExactly("places");
        assertThat(driver.exportTable(null, "places", destination, "EPSG:4326", true, visitor))
                .containsExactly("places");
    }

    @Test
    void readsAndWritesRowsThroughFileDriverContract() throws Exception {
        TestFileDriver driver = new TestFileDriver();

        assertThat(driver.getRowCount()).isZero();
        assertThat(driver.getFieldCount()).isEqualTo(2);
        assertThat(driver.getEstimatedRowSize(0)).isEqualTo(16);

        driver.insertRow(new Object[]{7, "north"});
        driver.insertRow(new Object[]{8, "south"});

        assertThat(driver.getRowCount()).isEqualTo(2);
        assertThat(driver.getField(0, 0)).isEqualTo(7);
        assertThat(driver.getField(0, 1)).isEqualTo("north");
        assertThat(driver.getField(1, 0)).isEqualTo(8);
        assertThat(driver.getField(1, 1)).isEqualTo("south");

        driver.close();
        assertThat(driver.closed).isTrue();
    }

    @Test
    void exposesImportDriverTypes() {
        assertThat(DriverFunction.IMPORT_DRIVER_TYPE.values())
                .containsExactly(DriverFunction.IMPORT_DRIVER_TYPE.LINK,
                        DriverFunction.IMPORT_DRIVER_TYPE.COPY);
        assertThat(DriverFunction.IMPORT_DRIVER_TYPE.valueOf("LINK"))
                .isEqualTo(DriverFunction.IMPORT_DRIVER_TYPE.LINK);
    }

    private static final class TestScalarFunction extends DeterministicScalarFunction {

        @Override
        public String getJavaStaticMethod() {
            return "evaluate";
        }
    }

    private static final class TestDriver implements DriverFunction {

        @Override
        public IMPORT_DRIVER_TYPE getImportDriverType() {
            return IMPORT_DRIVER_TYPE.COPY;
        }

        @Override
        public String[] getImportFormats() {
            return new String[]{"csv", "geojson"};
        }

        @Override
        public String[] getExportFormats() {
            return new String[]{"geojson"};
        }

        @Override
        public String getFormatDescription(String format) {
            return "geojson".equals(format) ? "GeoJSON" : "CSV";
        }

        @Override
        public boolean isSpatialFormat(String format) {
            return "geojson".equals(format);
        }

        @Override
        public String[] exportTable(Connection connection, String tableName, File file,
                ProgressVisitor progressVisitor) throws SQLException, IOException {
            return new String[]{tableName};
        }

        @Override
        public String[] exportTable(Connection connection, String tableName, File file,
                boolean deleteTable, ProgressVisitor progressVisitor)
                throws SQLException, IOException {
            return new String[]{tableName};
        }

        @Override
        public String[] exportTable(Connection connection, String tableName, File file,
                String encoding, boolean deleteTable, ProgressVisitor progressVisitor)
                throws SQLException, IOException {
            return new String[]{tableName};
        }

        @Override
        public String[] exportTable(Connection connection, String tableName, File file,
                String encoding, ProgressVisitor progressVisitor)
                throws SQLException, IOException {
            return new String[]{tableName};
        }

        @Override
        public String[] importFile(Connection connection, String tableName, File file,
                ProgressVisitor progressVisitor) throws SQLException, IOException {
            return new String[]{tableName};
        }

        @Override
        public String[] importFile(Connection connection, String tableName, File file,
                String encoding, ProgressVisitor progressVisitor)
                throws SQLException, IOException {
            return new String[]{tableName};
        }

        @Override
        public String[] importFile(Connection connection, String tableName, File file,
                boolean forceSchema, ProgressVisitor progressVisitor)
                throws SQLException, IOException {
            return new String[]{tableName};
        }

        @Override
        public String[] importFile(Connection connection, String tableName, File file,
                String encoding, boolean forceSchema, ProgressVisitor progressVisitor)
                throws SQLException, IOException {
            return new String[]{tableName};
        }
    }

    private static final class TestFileDriver implements FileDriver {

        private final List<Object[]> rows = new ArrayList<>();

        private boolean closed;

        @Override
        public long getRowCount() {
            return rows.size();
        }

        @Override
        public int getEstimatedRowSize(long rowId) {
            return 16;
        }

        @Override
        public int getFieldCount() {
            return 2;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public Object getField(long rowId, int fieldIndex) {
            return rows.get((int) rowId)[fieldIndex];
        }

        @Override
        public void insertRow(Object[] row) {
            rows.add(row);
        }
    }
}
