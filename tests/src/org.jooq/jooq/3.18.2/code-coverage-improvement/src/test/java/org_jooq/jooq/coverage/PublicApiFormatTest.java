package org_jooq.jooq.coverage;

import org.jooq.Binding;
import org.jooq.CSVFormat;
import org.jooq.Comparator;
import org.jooq.ChartFormat;
import org.jooq.ContextConverter;
import org.jooq.Converter;
import org.jooq.Converters;
import org.jooq.DDLExportConfiguration;
import org.jooq.DDLFlag;
import org.jooq.DatePart;
import org.jooq.FilePattern;
import org.jooq.FormattingProvider;
import org.jooq.SchemaMapping;
import org.jooq.Geography;
import org.jooq.Geometry;
import org.jooq.JSON;
import org.jooq.JSONB;
import org.jooq.JSONFormat;
import org.jooq.JoinType;
import org.jooq.MigrationConfiguration;
import org.jooq.Operator;
import org.jooq.SQLDialect;
import org.jooq.SQLDialectCategory;
import org.jooq.Source;
import org.jooq.TXTFormat;
import org.jooq.TableOptions;
import org.jooq.XML;
import org.jooq.XMLFormat;
import org.jooq.conf.InterpreterSearchSchema;
import org.jooq.conf.MappedCatalog;
import org.jooq.conf.MappedSchema;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PublicApiFormatTest {

    @Test
    void configuresCsvAndChartOutput() {
        CSVFormat csv = new CSVFormat()
                .delimiter(';')
                .nullString("NULL")
                .emptyString("EMPTY")
                .newline("\r\n")
                .quoteString("'")
                .quote(CSVFormat.Quote.ALWAYS)
                .header(true);

        assertThat(csv.delimiter()).isEqualTo(";");
        assertThat(csv.nullString()).isEqualTo("NULL");
        assertThat(csv.emptyString()).isEqualTo("EMPTY");
        assertThat(csv.newline()).isEqualTo("\r\n");
        assertThat(csv.quoteString()).isEqualTo("'");
        assertThat(csv.quote()).isEqualTo(CSVFormat.Quote.ALWAYS);
        assertThat(csv.header()).isTrue();

        DecimalFormat numbers = new DecimalFormat("0.00");
        DecimalFormat percentages = new DecimalFormat("0%");
        ChartFormat chart = new ChartFormat()
                .output(ChartFormat.Output.ASCII)
                .type(ChartFormat.Type.AREA)
                .display(ChartFormat.Display.STACKED)
                .dimensions(80, 20)
                .category(1)
                .categoryAsText(true)
                .values(2, 3)
                .shades('#', '+')
                .showLegends(true, false)
                .newline("\n")
                .numericFormat(numbers)
                .percentFormat(percentages);

        assertThat(chart.output()).isEqualTo(ChartFormat.Output.ASCII);
        assertThat(chart.type()).isEqualTo(ChartFormat.Type.AREA);
        assertThat(chart.display()).isEqualTo(ChartFormat.Display.STACKED);
        assertThat(chart.width()).isEqualTo(80);
        assertThat(chart.height()).isEqualTo(20);
        assertThat(chart.category()).isEqualTo(1);
        assertThat(chart.categoryAsText()).isTrue();
        assertThat(chart.values()).containsExactly(2, 3);
        assertThat(chart.shades()).containsExactly('#', '+');
        assertThat(chart.showHorizontalLegend()).isTrue();
        assertThat(chart.showVerticalLegend()).isFalse();
        assertThat(chart.newline()).isEqualTo("\n");
        assertThat(chart.numericFormat()).isSameAs(numbers);
        assertThat(chart.percentFormat()).isSameAs(percentages);
    }

    @Test
    void configuresJsonAndDdlExport() {
        JSONFormat json = new JSONFormat().mutable(true).format(true).newline("\n")
                .globalIndent(4).indent(2).header(true)
                .recordFormat(JSONFormat.RecordFormat.OBJECT)
                .wrapSingleColumnRecords(true).quoteNested(true);
        assertThat(json.mutable()).isTrue();
        assertThat(json.format()).isTrue();
        assertThat(json.newline()).isEqualTo("\n");
        assertThat(json.globalIndent()).isEqualTo(4);
        assertThat(json.indent()).isEqualTo(2);
        assertThat(json.indentString(3)).isEqualTo("          ");
        assertThat(json.header()).isTrue();
        assertThat(json.recordFormat()).isEqualTo(JSONFormat.RecordFormat.OBJECT);
        assertThat(json.wrapSingleColumnRecords()).isTrue();
        assertThat(json.quoteNested()).isTrue();

        DDLExportConfiguration ddl = new DDLExportConfiguration().flags(DDLFlag.TABLE, DDLFlag.INDEX)
                .createSchemaIfNotExists(true).createTableIfNotExists(true).createIndexIfNotExists(true)
                .createDomainIfNotExists(true).createSequenceIfNotExists(true).createViewIfNotExists(true)
                .createOrReplaceView(true).respectCatalogOrder(true).respectSchemaOrder(true)
                .respectTableOrder(true).respectColumnOrder(true).respectConstraintOrder(true)
                .respectIndexOrder(true).respectDomainOrder(true).respectSequenceOrder(true)
                .defaultSequenceFlags(true).includeConstraintsOnViews(true);
        assertThat(ddl.flags()).containsExactlyInAnyOrder(DDLFlag.TABLE, DDLFlag.INDEX);
        assertThat(ddl.createSchemaIfNotExists() && ddl.createTableIfNotExists() && ddl.createIndexIfNotExists()).isTrue();
        assertThat(ddl.createDomainIfNotExists() && ddl.createSequenceIfNotExists() && ddl.createViewIfNotExists()).isTrue();
        assertThat(ddl.createOrReplaceView()).isTrue();
        assertThat(ddl.respectCatalogOrder() && ddl.respectSchemaOrder() && ddl.respectTableOrder()
                && ddl.respectColumnOrder() && ddl.respectConstraintOrder() && ddl.respectIndexOrder()
                && ddl.respectDomainOrder() && ddl.respectSequenceOrder()).isTrue();
        assertThat(ddl.defaultSequenceFlags() && ddl.includeConstraintsOnViews()).isTrue();
    }

    @Test
    void convertsValuesAndArraysInBothDirections() {
        Converter<String, Integer> converter = Converter.of(String.class, Integer.class, Integer::valueOf, Object::toString);
        assertThat(converter.from("42")).isEqualTo(42);
        assertThat(converter.to(42)).isEqualTo("42");
        assertThat(converter.inverse().from(42)).isEqualTo("42");
        assertThat(converter.andThen(Converter.of(Integer.class, BigDecimal.class, BigDecimal::valueOf, BigDecimal::intValue))
                .from("42")).isEqualTo(new BigDecimal("42"));
        assertThat(converter.forArrays().from(new String[] { "1", "2" })).containsExactly(1, 2);
        assertThat(Converter.from(String.class, Integer.class, Integer::valueOf).from("7")).isEqualTo(7);
        assertThat(Converter.to(String.class, Integer.class, Object::toString).to(7)).isEqualTo("7");
        assertThat(Converter.ofNullable(String.class, Integer.class, Integer::valueOf, Object::toString).from(null)).isNull();
        assertThat(Converter.fromNullable(String.class, Integer.class, Integer::valueOf).from(null)).isNull();
        assertThat(Converter.toNullable(String.class, Integer.class, Object::toString).to(null)).isNull();

        ContextConverter<String, Integer> contextual = ContextConverter.of(String.class, Integer.class,
                (value, context) -> Integer.valueOf(value), (value, context) -> value.toString());
        assertThat(contextual.from("9")).isEqualTo(9);
        assertThat(contextual.to(9)).isEqualTo("9");
        assertThat(contextual.inverse().from(9)).isEqualTo("9");
        assertThat(contextual.forArrays().from(new String[] { "3" })).containsExactly(3);
        assertThat(ContextConverter.from(String.class, Integer.class,
                (value, context) -> Integer.valueOf(value)).from("6")).isEqualTo(6);
        assertThat(ContextConverter.to(String.class, Integer.class,
                (value, context) -> value.toString()).to(6)).isEqualTo("6");
        assertThat(ContextConverter.fromNullable(String.class, Integer.class,
                (value, context) -> Integer.valueOf(value)).from(null)).isNull();
        assertThat(ContextConverter.toNullable(String.class, Integer.class,
                (value, context) -> value.toString()).to(null)).isNull();
        assertThat(Converters.of(converter).from("8")).isEqualTo(8);
        assertThat(Converters.forArrays(converter).from(new String[] { "4" })).containsExactly(4);
        assertThat(Converters.forArrayComponents(Converters.forArrays(converter)).from("5")).isEqualTo(5);

        Binding<String, Integer> basicBinding = Binding.of(converter, context -> { }, context -> { }, context -> { });
        Binding<String, Integer> statementBinding = Binding.of(converter, context -> { }, context -> { }, context -> { },
                context -> { }, context -> { });
        Binding<String, Integer> completeBinding = Binding.of(converter, context -> { }, context -> { }, context -> { },
                context -> { }, context -> { }, context -> { }, context -> { });
        assertThat(basicBinding.converter()).isSameAs(converter);
        assertThat(statementBinding.converter()).isSameAs(converter);
        assertThat(completeBinding.converter()).isSameAs(converter);
    }

    @Test
    void representsDataValuesAndMigrationOptions() {
        assertThat(JSON.json("{\"a\":1}")).isEqualTo(JSON.valueOf("{\"a\":1}"));
        assertThat(JSON.jsonOrNull(null)).isNull();
        assertThat(JSONB.jsonb("{\"a\":1}").data()).isEqualTo("{\"a\":1}");
        assertThat(JSONB.jsonbOrNull(null)).isNull();
        assertThat(Geometry.geometry("POINT (1 2)")).isEqualTo(Geometry.valueOf("POINT (1 2)"));
        assertThat(Geometry.geometryOrNull(null)).isNull();
        assertThat(Geography.geography("POINT (1 2)")).isEqualTo(Geography.valueOf("POINT (1 2)"));
        assertThat(Geography.geographyOrNull(null)).isNull();

        MigrationConfiguration migration = new MigrationConfiguration().alterTableAddMultiple(true)
                .alterTableDropMultiple(true).dropSchemaCascade(true).dropTableCascade(true)
                .alterTableDropCascade(true).createOrReplaceView(true).respectColumnOrder(true);
        assertThat(migration.alterTableAddMultiple() && migration.alterTableDropMultiple()
                && migration.dropSchemaCascade() && migration.dropTableCascade() && migration.alterTableDropCascade()
                && migration.createOrReplaceView() && migration.respectColumnOrder()).isTrue();
    }

    @Test
    void configuresAdditionalFormatsAndValueSemantics() {
        TXTFormat text = new TXTFormat().minColWidth(3).horizontalTableBorder(false)
                .horizontalHeaderBorder(false).horizontalCellBorder(true).verticalTableBorder(false)
                .verticalCellBorder(true).intersectLines(true);
        assertThat(text.minColWidth()).isEqualTo(3);
        assertThat(text.horizontalTableBorder() && text.horizontalHeaderBorder()).isFalse();
        assertThat(text.horizontalCellBorder() && text.verticalCellBorder() && text.intersectLines()).isTrue();
        assertThat(text.verticalTableBorder()).isFalse();

        XMLFormat xmlFormat = new XMLFormat().mutable(true).xmlns(true).format(true).newline("\\n")
                .globalIndent(2).indent(3).header(true).recordFormat(XMLFormat.RecordFormat.COLUMN_NAME_ELEMENTS)
                .quoteNested(true);
        assertThat(xmlFormat.mutable() && xmlFormat.xmlns() && xmlFormat.format() && xmlFormat.header()
                && xmlFormat.quoteNested()).isTrue();
        assertThat(xmlFormat.newline()).isEqualTo("\\n");
        assertThat(xmlFormat.globalIndent()).isEqualTo(2);
        assertThat(xmlFormat.indent()).isEqualTo(3);
        assertThat(xmlFormat.indentString(2)).isEmpty();
        assertThat(xmlFormat.recordFormat()).isEqualTo(XMLFormat.RecordFormat.COLUMN_NAME_ELEMENTS);

        assertThat(JSON.json("{\"a\":1}").data()).isEqualTo("{\"a\":1}");
        assertThat(JSON.json("{\"a\":1}").toString()).isEqualTo("{\"a\":1}");
        assertThat(JSONB.valueOf("{\"a\":1}")).isEqualTo(JSONB.jsonb("{\"a\":1}"));
        assertThat(JSONB.valueOf("{\"a\":1}").toString()).isEqualTo("{\"a\":1}");
        assertThat(Geometry.geometry("POINT (1 2)").toString()).isEqualTo("POINT (1 2)");
        assertThat(Geography.geography("POINT (1 2)").toString()).isEqualTo("POINT (1 2)");
        assertThat(XML.xml("<a/>")).isEqualTo(XML.valueOf("<a/>"));
        assertThat(XML.xmlOrNull(null)).isNull();
    }

    @Test
    void readsSourcesAcrossPublicInputForms() throws Exception {
        byte[] bytes = "café".getBytes(StandardCharsets.UTF_8);
        assertThat(Source.of("plain").readString()).isEqualTo("plain");
        assertThat(Source.of(bytes).readString()).isEqualTo("café");
        assertThat(Source.of(bytes, "UTF-8").readString()).isEqualTo("café");
        assertThat(Source.of(bytes, StandardCharsets.UTF_8).readString()).isEqualTo("café");
        assertThat(Source.of(bytes, StandardCharsets.UTF_8.newDecoder()).readString()).isEqualTo("café");
        assertThat(Source.of(new StringReader("reader"), 2).readString()).isEqualTo("re");
        assertThat(Source.of(new ByteArrayInputStream(bytes), 2, StandardCharsets.UTF_8).readString()).isEqualTo("ca");
        assertThat(Source.of(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8.newDecoder()).readString()).isEqualTo("café");

        File file = File.createTempFile("jooq-source", ".txt");
        java.nio.file.Files.writeString(file.toPath(), "file", StandardCharsets.UTF_8);
        Source source = Source.of(file, StandardCharsets.UTF_8);
        assertThat(source.readString()).isEqualTo("file");
        assertThat(source.toString()).contains(file.getName());
        assertThat(file.delete()).isTrue();
    }

    @Test
    void exposesComparisonDialectTableAndJaxbConfiguration() throws Exception {
        assertThat(Comparator.EQUALS.toSQL()).isEqualTo("=");
        assertThat(Comparator.EQUALS.toKeyword()).isNotNull();
        assertThat(Comparator.EQUALS.inverse()).isEqualTo(Comparator.NOT_EQUALS);
        assertThat(Comparator.LESS.mirror()).isEqualTo(Comparator.GREATER);
        assertThat(Comparator.IN.supportsNulls()).isFalse();
        boolean supportsQuantifier = Comparator.IN.supportsQuantifier();
        boolean supportsSubselect = Comparator.IN.supportsSubselect();
        assertThat(supportsQuantifier && supportsSubselect).isFalse();
        assertThat(SQLDialect.H2.precedes(SQLDialect.H2)).isTrue();
        assertThat(SQLDialect.H2.precedesStrictly(SQLDialect.H2)).isFalse();
        assertThat(SQLDialect.H2.supports(List.of(SQLDialect.H2))).isTrue();
        assertThat(SQLDialect.H2.supportsDatabaseVersion(2, 1, null)).isFalse();
        assertThat(SQLDialect.H2.thirdParty().driver()).isNotBlank();
        assertThat(SQLDialectCategory.OTHER.dialects()).contains(SQLDialect.H2);
        assertThat(SQLDialectCategory.OTHER.families()).contains(SQLDialect.H2);

        TableOptions table = TableOptions.temporaryTable(TableOptions.OnCommit.DELETE_ROWS);
        assertThat(table.type().isTable()).isTrue();
        assertThat(table.onCommit()).isEqualTo(TableOptions.OnCommit.DELETE_ROWS);
        assertThat(TableOptions.function("f").source()).isEqualTo("f");
        assertThat(TableOptions.function()).isEqualTo(TableOptions.function());
        assertThat(TableOptions.view()).isEqualTo(TableOptions.view());
        assertThat(TableOptions.materializedView()).isEqualTo(TableOptions.materializedView());
        assertThat(TableOptions.of(TableOptions.TableType.TEMPORARY)).isEqualTo(TableOptions.temporaryTable());
        assertThat(table.toString()).contains("TEMPORARY");

        InterpreterSearchSchema searchSchema = new InterpreterSearchSchema().withCatalog("catalog").withSchema("schema");
        assertThat(searchSchema.getCatalog()).isEqualTo("catalog");
        assertThat(searchSchema.getSchema()).isEqualTo("schema");
        InterpreterSearchSchema sameSearchSchema = new InterpreterSearchSchema();
        sameSearchSchema.setCatalog("catalog");
        sameSearchSchema.setSchema("schema");
        assertThat(searchSchema).isEqualTo(sameSearchSchema);
        MappedSchema mappedSchema = new MappedSchema().withInput("in").withInputExpression(Pattern.compile("in.*"))
                .withOutput("out");
        MappedCatalog mappedCatalog = new MappedCatalog().withInput("catalog").withOutput("target")
                .withSchemata(mappedSchema);
        assertThat(mappedCatalog.getSchemata()).containsExactly(mappedSchema);
        assertThat(mappedCatalog.getInputExpression()).isNull();
        assertThat(mappedCatalog.toString()).contains("catalog");
    }

    @Test
    void usesFormattingCallbacksAndJaxbAdapters() throws Exception {
        FormattingProvider provider = FormattingProvider.onTxtFormat(() -> new TXTFormat().minColWidth(4))
                .onCsvFormat(() -> new CSVFormat().delimiter(';'))
                .onJsonFormatForResults(() -> new JSONFormat().header(true))
                .onJsonFormatForRecords(() -> new JSONFormat().format(true))
                .onXmlFormatForResults(() -> new XMLFormat().header(true))
                .onXmlFormatForRecords(() -> new XMLFormat().format(true))
                .onChartFormat(() -> new ChartFormat().width(72))
                .onWidth(String::length);
        assertThat(provider.txtFormat().minColWidth()).isEqualTo(4);
        assertThat(provider.csvFormat().delimiter()).isEqualTo(";");
        assertThat(provider.jsonFormatForResults().header()).isTrue();
        assertThat(provider.jsonFormatForRecords().format()).isTrue();
        assertThat(provider.xmlFormatForResults().header()).isTrue();
        assertThat(provider.xmlFormatForRecords().format()).isTrue();
        assertThat(provider.chartFormat().width()).isEqualTo(72);
        assertThat(provider.width("jOOQ")).isEqualTo(4);

    }

    @Test
    void mapsSchemasAndFilesForSqlRendering() {
        org.jooq.Schema source = DSL.schema("legacy");
        org.jooq.Schema target = DSL.schema("application");
        SchemaMapping mapping = new SchemaMapping(new DefaultConfiguration());
        mapping.add(source, target);
        mapping.setDefaultSchema("application");
        assertThat(mapping.toString()).isNotEmpty();

        FilePattern pattern = new FilePattern().basedir(new File(".")).pattern("*.gradle")
                .sort(FilePattern.Sort.SEMANTIC);
        File build = new File("build.gradle");
        assertThat(pattern.sort()).isEqualTo(FilePattern.Sort.SEMANTIC);
        assertThat(pattern.path(build)).isEqualTo("build.gradle");
        assertThat(pattern.pathFile(build)).isEqualTo(build);
        assertThat(pattern.fileComparator().compare(new File("a2.sql"), new File("a10.sql"))).isLessThan(0);
    }

    @Test
    void resolvesPatternsAndDialectPresentation() {
        FilePattern pattern = new FilePattern().basedir(new File(".")).pattern("*.gradle").encoding("UTF-8")
                .sort(FilePattern.Sort.SEMANTIC);
        assertThat(pattern.basedir()).isEqualTo(new File("."));
        assertThat(pattern.pattern()).isEqualTo("*.gradle");
        assertThat(pattern.encoding()).isEqualTo("UTF-8");
        assertThat(pattern.matches("build.gradle")).isTrue();
        assertThat(pattern.collect()).isNotEmpty();
        assertThat(pattern.toString()).contains("*.gradle");
        assertThat(FilePattern.Sort.of("semantic")).isEqualTo(FilePattern.Sort.SEMANTIC);

        assertThat(SQLDialect.H2.supported()).isTrue();
        assertThat(SQLDialect.H2.supports(SQLDialect.H2)).isTrue();
        assertThat(SQLDialect.H2.getNameLC()).isEqualTo("h2");
        assertThat(SQLDialect.H2.getNameUC()).isEqualTo("H2");
        assertThat(SQLDialect.H2.getName()).isNotBlank();
        assertThat(SQLDialect.H2.category()).isNotNull();
        assertThat(SQLDialect.H2.thirdParty()).isNotNull();
        assertThat(DatePart.YEAR.toSQL()).isEqualTo("year");
        assertThat(DatePart.YEAR.toKeyword()).isNotNull();
        assertThat(DatePart.YEAR.toName()).isNotNull();
        assertThat(JoinType.LEFT_OUTER_JOIN.toSQL()).contains("left");
        assertThat(JoinType.LEFT_OUTER_JOIN.toKeyword(true)).isNotNull();
        assertThat(Operator.AND.identity()).isNotNull();
    }
}
