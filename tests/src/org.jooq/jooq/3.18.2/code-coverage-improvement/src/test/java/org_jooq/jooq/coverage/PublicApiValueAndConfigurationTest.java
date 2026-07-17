package org_jooq.jooq.coverage;

import org.jooq.ChartFormat;
import org.jooq.ContextConverter;
import org.jooq.Converter;
import org.jooq.Converters;
import org.jooq.DDLExportConfiguration;
import org.jooq.Decfloat;
import org.jooq.JSONFormat;
import org.jooq.SQLDialect;
import org.jooq.Source;
import org.jooq.conf.AutoAliasExpressions;
import org.jooq.conf.BackslashEscaping;
import org.jooq.conf.DiagnosticsConnection;
import org.jooq.conf.ExecuteWithoutWhere;
import org.jooq.conf.FetchIntermediateResult;
import org.jooq.conf.FetchTriggerValuesAfterReturning;
import org.jooq.conf.InterpreterNameLookupCaseSensitivity;
import org.jooq.conf.InterpreterSearchSchema;
import org.jooq.conf.InvocationOrder;
import org.jooq.conf.MappedCatalog;
import org.jooq.conf.MappedSchema;
import org.jooq.conf.MappedTable;
import org.jooq.conf.MappedUDT;
import org.jooq.conf.MigrationDefaultContentType;
import org.jooq.conf.MigrationSchema;
import org.jooq.conf.MigrationType;
import org.jooq.conf.NestedCollectionEmulation;
import org.jooq.conf.ObjectFactory;
import org.jooq.conf.ParamCastMode;
import org.jooq.conf.ParamType;
import org.jooq.conf.ParseNameCase;
import org.jooq.conf.ParseSearchSchema;
import org.jooq.conf.ParseUnknownFunctions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicApiValueAndConfigurationTest {

    @Test
    void configuresChartAndDdlMaterializedViewOptions() {
        ChartFormat chart = new ChartFormat().width(120).height(40)
                .showHorizontalLegend(false).showVerticalLegend(true);
        assertThat(chart.width()).isEqualTo(120);
        assertThat(chart.height()).isEqualTo(40);
        assertThat(chart.showHorizontalLegend()).isFalse();
        assertThat(chart.showVerticalLegend()).isTrue();

        DDLExportConfiguration ddl = new DDLExportConfiguration()
                .createMaterializedViewIfNotExists(true)
                .createOrReplaceMaterializedView(true)
                .inlineForeignKeyConstraints(DDLExportConfiguration.InlineForeignKeyConstraints.ALWAYS);
        assertThat(ddl.createMaterializedViewIfNotExists()).isTrue();
        assertThat(ddl.createOrReplaceMaterializedView()).isTrue();
        assertThat(ddl.inlineForeignKeyConstraints())
                .isEqualTo(DDLExportConfiguration.InlineForeignKeyConstraints.ALWAYS);
    }

    @Test
    void convertsNullableValuesAndComposesConverters() {
        Converter<String, Integer> numbers = Converter.ofNullable(String.class, Integer.class,
                Integer::valueOf, Object::toString, true, true);
        Converter<Integer, Long> longs = Converter.of(Integer.class, Long.class, Long::valueOf, Long::intValue);
        Converter<String, Long> composed = numbers.andThen(longs);
        assertThat(numbers.fromSupported()).isTrue();
        assertThat(numbers.toSupported()).isTrue();
        assertThat(composed.from("42")).isEqualTo(42L);
        assertThat(numbers.inverse().from(7)).isEqualTo("7");
        assertThat(numbers.forArrays().to(new Integer[] { 1, 2 })).containsExactly("1", "2");

        ContextConverter<String, Integer> contextual = ContextConverter.ofNullable(String.class, Integer.class,
                (value, context) -> Integer.valueOf(value), (value, context) -> value.toString(), true, true);
        assertThat(contextual.from("11")).isEqualTo(11);
        assertThat(contextual.to(11)).isEqualTo("11");
        assertThat(Converters.inverse(numbers).from(12)).isEqualTo("12");
        assertThatThrownBy(Converters::of).isInstanceOf(ArrayIndexOutOfBoundsException.class);
        assertThat(Converters.of(numbers, longs, Converter.of(Long.class, String.class, Object::toString, Long::valueOf))
                .from("4")).isEqualTo("4");
        ContextConverter<String, Integer> wrapped = Converters.of(numbers);
        assertThat(wrapped.to(3, null)).isEqualTo("3");
        assertThat(wrapped.toSupported()).isTrue();
        assertThat(wrapped.fromSupported()).isTrue();
        assertThat(wrapped.toString()).contains("Converters");
    }

    @Test
    void representsDecimalFloatingPointSpecialValues() {
        Decfloat finite = Decfloat.decfloat("42.5");
        assertThat(finite.data()).isEqualTo("42.5");
        assertThat(Decfloat.valueOf("42.5")).isEqualTo(finite);
        assertThat(finite.doubleValue()).isEqualTo(42.5d);
        assertThat(finite.floatValue()).isEqualTo(42.5f);
        assertThat(finite.intValue()).isEqualTo(42);
        assertThat(finite.longValue()).isEqualTo(42L);
        assertThat(finite.hashCode()).isEqualTo(Decfloat.decfloat("42.5").hashCode());
        assertThat(finite.toString()).isEqualTo("4.25E1");
        assertThat(Decfloat.decfloatOrNull(null)).isNull();
        assertThat(Decfloat.decfloat("NaN").isNaN()).isTrue();
        assertThat(Decfloat.decfloat("Infinity").isPositiveInfinity()).isTrue();
        assertThat(Decfloat.decfloat("-Infinity").isNegativeInfinity()).isTrue();
    }

    @Test
    void preservesJsonNullPoliciesAndSourceIdentity() throws Exception {
        JSONFormat format = new JSONFormat().objectNulls(JSONFormat.NullFormat.NULL_ON_NULL)
                .arrayNulls(JSONFormat.NullFormat.ABSENT_ON_NULL);
        assertThat(format.objectNulls()).isEqualTo(JSONFormat.NullFormat.NULL_ON_NULL);
        assertThat(format.arrayNulls()).isEqualTo(JSONFormat.NullFormat.ABSENT_ON_NULL);

        File file = File.createTempFile("jooq-source", ".sql");
        java.nio.file.Files.writeString(file.toPath(), "select 1", StandardCharsets.UTF_8);
        Source fromFile = Source.of(file);
        assertThat(fromFile.file()).isEqualTo(file);
        assertThat(fromFile.name()).isEqualTo(file.getName());
        assertThat(Source.of(new ByteArrayInputStream("select 2".getBytes(StandardCharsets.UTF_8))).readString())
                .isEqualTo("select 2");
        assertThat(Source.resolve(file.getAbsolutePath()).readString()).isEqualTo("select 1");
        assertThat(file.delete()).isTrue();
    }

    @Test
    void configuresJaxbMappingObjectsAndFactoryProducts() throws Exception {
        Pattern users = Pattern.compile("users_.*");
        MappedTable table = new MappedTable().withInput("users").withInputExpression(users).withOutput("app_users");
        MappedUDT udt = new MappedUDT().withInput("address").withInputExpression(Pattern.compile("address"))
                .withOutput("app_address");
        MappedSchema schema = new MappedSchema().withInput("public").withOutput("app")
                .withTables(List.of(table)).withUdts(List.of(udt));
        MappedCatalog catalog = new MappedCatalog().withInput("database").withOutput("app_database")
                .withSchemata(List.of(schema));
        assertThat(table.getInput()).isEqualTo("users");
        assertThat(table.getInputExpression()).isEqualTo(users);
        assertThat(table.getOutput()).isEqualTo("app_users");
        assertThat(table).isEqualTo(new MappedTable().withInput("users").withInputExpression(users).withOutput("app_users"));
        assertThat(udt.getOutput()).isEqualTo("app_address");
        assertThat(schema.getTables()).containsExactly(table);
        assertThat(schema.getUdts()).containsExactly(udt);
        assertThat(catalog.getSchemata()).containsExactly(schema);

        MigrationSchema migrationSchema = new MigrationSchema().withCatalog("database").withSchema("public");
        MigrationType migration = new MigrationType().withSchemata(migrationSchema);
        ParseSearchSchema parseSchema = new ParseSearchSchema().withCatalog("database").withSchema("public");
        InterpreterSearchSchema interpreter = new InterpreterSearchSchema().withCatalog("database").withSchema("public");
        assertThat(migration.getSchemata()).containsExactly(migrationSchema);
        assertThat(parseSchema).isEqualTo(new ParseSearchSchema().withCatalog("database").withSchema("public"));
        assertThat(interpreter.toString()).contains("database");

        ObjectFactory factory = new ObjectFactory();
        assertThat(factory.createSettings().getRenderMapping()).isNull();
        assertThat(factory.createMappedCatalog()).isNotNull();
        assertThat(factory.createMappedSchema()).isNotNull();
        assertThat(factory.createMappedTable()).isNotNull();
        assertThat(factory.createMappedUDT()).isNotNull();
        assertThat(factory.createMigrationSchema()).isNotNull();
        assertThat(factory.createParseSearchSchema()).isNotNull();
        assertThat(factory.createInterpreterSearchSchema()).isNotNull();
        assertThat(factory.createRenderFormatting()).isNotNull();
        assertThat(factory.createRenderMapping()).isNotNull();
    }

    @Test
    void serializesConfigurationEnums() {
        assertThat(AutoAliasExpressions.fromValue(AutoAliasExpressions.NEVER.value())).isEqualTo(AutoAliasExpressions.NEVER);
        assertThat(BackslashEscaping.fromValue(BackslashEscaping.DEFAULT.value())).isEqualTo(BackslashEscaping.DEFAULT);
        assertThat(DiagnosticsConnection.fromValue(DiagnosticsConnection.DEFAULT.value())).isEqualTo(DiagnosticsConnection.DEFAULT);
        assertThat(ExecuteWithoutWhere.fromValue(ExecuteWithoutWhere.LOG_DEBUG.value())).isEqualTo(ExecuteWithoutWhere.LOG_DEBUG);
        assertThat(FetchIntermediateResult.fromValue(FetchIntermediateResult.WHEN_RESULT_REQUESTED.value()))
                .isEqualTo(FetchIntermediateResult.WHEN_RESULT_REQUESTED);
        assertThat(FetchTriggerValuesAfterReturning.fromValue(FetchTriggerValuesAfterReturning.WHEN_NEEDED.value()))
                .isEqualTo(FetchTriggerValuesAfterReturning.WHEN_NEEDED);
        assertThat(InterpreterNameLookupCaseSensitivity.fromValue(InterpreterNameLookupCaseSensitivity.DEFAULT.value()))
                .isEqualTo(InterpreterNameLookupCaseSensitivity.DEFAULT);
        assertThat(InvocationOrder.fromValue(InvocationOrder.DEFAULT.value())).isEqualTo(InvocationOrder.DEFAULT);
        assertThat(MigrationDefaultContentType.fromValue(MigrationDefaultContentType.SCRIPT.value()))
                .isEqualTo(MigrationDefaultContentType.SCRIPT);
        assertThat(NestedCollectionEmulation.fromValue(NestedCollectionEmulation.DEFAULT.value()))
                .isEqualTo(NestedCollectionEmulation.DEFAULT);
        assertThat(ParamCastMode.fromValue(ParamCastMode.DEFAULT.value())).isEqualTo(ParamCastMode.DEFAULT);
        assertThat(ParamType.fromValue(ParamType.INDEXED.value())).isEqualTo(ParamType.INDEXED);
        assertThat(ParseNameCase.fromValue(ParseNameCase.AS_IS.value())).isEqualTo(ParseNameCase.AS_IS);
        assertThat(ParseUnknownFunctions.fromValue(ParseUnknownFunctions.FAIL.value())).isEqualTo(ParseUnknownFunctions.FAIL);
        assertThat(SQLDialect.H2.commercial()).isFalse();
    }
}
