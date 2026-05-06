/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_bigquery;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.bigquery.Acl;
import com.google.cloud.bigquery.BigQueryError;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Clustering;
import com.google.cloud.bigquery.ColumnReference;
import com.google.cloud.bigquery.ConnectionProperty;
import com.google.cloud.bigquery.CopyJobConfiguration;
import com.google.cloud.bigquery.CsvOptions;
import com.google.cloud.bigquery.DatasetId;
import com.google.cloud.bigquery.DatasetInfo;
import com.google.cloud.bigquery.EncryptionConfiguration;
import com.google.cloud.bigquery.ExternalDatasetReference;
import com.google.cloud.bigquery.ExternalTableDefinition;
import com.google.cloud.bigquery.ExtractJobConfiguration;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldElementType;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.ForeignKey;
import com.google.cloud.bigquery.FormatOptions;
import com.google.cloud.bigquery.HivePartitioningOptions;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.LoadJobConfiguration;
import com.google.cloud.bigquery.MaterializedViewDefinition;
import com.google.cloud.bigquery.ModelId;
import com.google.cloud.bigquery.PolicyTags;
import com.google.cloud.bigquery.PrimaryKey;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.Range;
import com.google.cloud.bigquery.RangePartitioning;
import com.google.cloud.bigquery.RemoteFunctionOptions;
import com.google.cloud.bigquery.RoutineArgument;
import com.google.cloud.bigquery.RoutineId;
import com.google.cloud.bigquery.RoutineInfo;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLDataType;
import com.google.cloud.bigquery.StandardSQLField;
import com.google.cloud.bigquery.StandardSQLStructType;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.TableConstraints;
import com.google.cloud.bigquery.TableDefinition;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableInfo;
import com.google.cloud.bigquery.TimePartitioning;
import com.google.cloud.bigquery.UserDefinedFunction;
import com.google.cloud.bigquery.ViewDefinition;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Google_cloud_bigqueryTest {
    private static final String PROJECT = "sample-project";
    private static final String DATASET = "analytics";
    private static final String TABLE = "events";
    private static final String LOCATION = "us-central1";

    @Test
    void identifiersOptionsAndErrorsExposeConfiguredValues() {
        DatasetId datasetId = DatasetId.of(PROJECT, DATASET);
        TableId tableId = TableId.of(PROJECT, DATASET, TABLE);
        JobId jobId = JobId.newBuilder()
                .setProject(PROJECT)
                .setJob("query-job")
                .setLocation(LOCATION)
                .build();
        ModelId modelId = ModelId.of(PROJECT, DATASET, "churn_model");
        RoutineId routineId = RoutineId.of(PROJECT, DATASET, "normalize_event");
        BigQueryError error = new BigQueryError("invalid", "query", "Invalid query", "debug details");
        BigQueryOptions options = BigQueryOptions.newBuilder()
                .setProjectId(PROJECT)
                .setLocation(LOCATION)
                .setUseInt64Timestamps(true)
                .build();

        options.setThrowNotFound(false);
        options.setQueryPreviewEnabled("true");

        assertThat(datasetId.getProject()).isEqualTo(PROJECT);
        assertThat(datasetId.getDataset()).isEqualTo(DATASET);
        assertThat(tableId.getIAMResourceName()).contains(PROJECT, DATASET, TABLE);
        assertThat(jobId.getProject()).isEqualTo(PROJECT);
        assertThat(jobId.getJob()).isEqualTo("query-job");
        assertThat(jobId.getLocation()).isEqualTo(LOCATION);
        assertThat(jobId.toBuilder().setJob("other-job").build().getJob()).isEqualTo("other-job");
        assertThat(modelId.getModel()).isEqualTo("churn_model");
        assertThat(routineId.getRoutine()).isEqualTo("normalize_event");
        assertThat(error.getReason()).isEqualTo("invalid");
        assertThat(error.getDebugInfo()).isEqualTo("debug details");
        assertThat(options.getProjectId()).isEqualTo(PROJECT);
        assertThat(options.getLocation()).isEqualTo(LOCATION);
        assertThat(options.getUseInt64Timestamps()).isTrue();
        assertThat(options.getThrowNotFound()).isFalse();
        assertThat(options.isQueryPreviewEnabled()).isTrue();
    }

    @Test
    void datasetInfoModelsAccessControlsDefaultsAndExternalReferences() {
        EncryptionConfiguration encryption = EncryptionConfiguration.newBuilder()
                .setKmsKeyName("projects/sample-project/locations/us/keyRings/analytics/cryptoKeys/default")
                .build();
        ExternalDatasetReference externalReference = ExternalDatasetReference.newBuilder()
                .setConnection("projects/sample-project/locations/us/connections/external-catalog")
                .setExternalSource("aws-glue://catalogs/analytics")
                .build();
        Acl.Expr condition = new Acl.Expr(
                "request.time < timestamp('2030-01-01T00:00:00Z')",
                "temporary access",
                "Expires automatically",
                "us");
        Acl userReader = Acl.of(new Acl.User("analyst@example.com"), Acl.Role.READER, condition);
        Acl projectReaders = Acl.of(Acl.Group.ofProjectReaders(), Acl.Role.READER);
        Acl authorizedView = Acl.of(new Acl.View(TableId.of(PROJECT, DATASET, "shared_events_view")));
        Acl authorizedRoutine = Acl.of(new Acl.Routine(RoutineId.of(PROJECT, DATASET, "mask_email")));
        Acl authorizedDataset = Acl.of(new Acl.DatasetAclEntity(
                DatasetId.of(PROJECT, "shared_reference"),
                List.of("VIEWS")));
        DatasetInfo datasetInfo = DatasetInfo.newBuilder(DatasetId.of(PROJECT, DATASET))
                .setFriendlyName("Analytics dataset")
                .setDescription("Curated analytics data")
                .setLocation(LOCATION)
                .setDefaultTableLifetime(86_400_000L)
                .setDefaultPartitionExpirationMs(604_800_000L)
                .setDefaultCollation("und:ci")
                .setDefaultEncryptionConfiguration(encryption)
                .setStorageBillingModel("PHYSICAL")
                .setMaxTimeTravelHours(96L)
                .setLabels(Map.of("env", "test"))
                .setResourceTags(Map.of("tagKeys/456", "tagValues/789"))
                .setExternalDatasetReference(externalReference)
                .setAcl(List.of(userReader, projectReaders, authorizedView, authorizedRoutine, authorizedDataset))
                .build();

        assertThat(datasetInfo.getDatasetId()).isEqualTo(DatasetId.of(PROJECT, DATASET));
        assertThat(datasetInfo.getFriendlyName()).isEqualTo("Analytics dataset");
        assertThat(datasetInfo.getDescription()).isEqualTo("Curated analytics data");
        assertThat(datasetInfo.getLocation()).isEqualTo(LOCATION);
        assertThat(datasetInfo.getDefaultTableLifetime()).isEqualTo(86_400_000L);
        assertThat(datasetInfo.getDefaultPartitionExpirationMs()).isEqualTo(604_800_000L);
        assertThat(datasetInfo.getDefaultCollation()).isEqualTo("und:ci");
        assertThat(datasetInfo.getDefaultEncryptionConfiguration()).isEqualTo(encryption);
        assertThat(datasetInfo.getStorageBillingModel()).isEqualTo("PHYSICAL");
        assertThat(datasetInfo.getMaxTimeTravelHours()).isEqualTo(96L);
        assertThat(datasetInfo.getLabels()).containsEntry("env", "test");
        assertThat(datasetInfo.getResourceTags()).containsEntry("tagKeys/456", "tagValues/789");
        assertThat(datasetInfo.getExternalDatasetReference().getConnection()).endsWith("/connections/external-catalog");
        assertThat(datasetInfo.getExternalDatasetReference().getExternalSource())
                .isEqualTo("aws-glue://catalogs/analytics");
        assertThat(datasetInfo.getAcl()).containsExactly(
                userReader,
                projectReaders,
                authorizedView,
                authorizedRoutine,
                authorizedDataset);
        assertThat(((Acl.User) datasetInfo.getAcl().get(0).getEntity()).getEmail()).isEqualTo("analyst@example.com");
        assertThat(datasetInfo.getAcl().get(0).getCondition()).isEqualTo(condition);
        assertThat(((Acl.Group) datasetInfo.getAcl().get(1).getEntity()).getIdentifier()).isEqualTo("projectReaders");
        assertThat(((Acl.View) datasetInfo.getAcl().get(2).getEntity()).getId().getTable())
                .isEqualTo("shared_events_view");
        assertThat(((Acl.Routine) datasetInfo.getAcl().get(3).getEntity()).getId().getRoutine())
                .isEqualTo("mask_email");
        assertThat(((Acl.DatasetAclEntity) datasetInfo.getAcl().get(4).getEntity()).getTargetTypes())
                .containsExactly("VIEWS");
        assertThat(datasetInfo.toBuilder().setFriendlyName("Renamed dataset").build().getFriendlyName())
                .isEqualTo("Renamed dataset");
        assertThat(encryption.toBuilder()
                .setKmsKeyName("projects/sample-project/locations/us/keyRings/analytics/cryptoKeys/other")
                .build()
                .getKmsKeyName()).endsWith("/cryptoKeys/other");
    }

    @Test
    void schemaSupportsNestedFieldsRangeTypesPolicyTagsAndCaseInsensitiveLookup() {
        PolicyTags policyTags = PolicyTags.newBuilder()
                .setNames(List.of("projects/sample-project/locations/us/taxonomies/pii/policyTags/email"))
                .build();
        FieldElementType dateElementType = FieldElementType.newBuilder().setType("DATE").build();
        Field eventAttributes = Field.newBuilder("attributes", StandardSQLTypeName.STRUCT,
                        Field.newBuilder("source", StandardSQLTypeName.STRING)
                                .setMaxLength(32L)
                                .setCollation("und:ci")
                                .build(),
                        Field.newBuilder("score", StandardSQLTypeName.NUMERIC)
                                .setPrecision(10L)
                                .setScale(2L)
                                .setDefaultValueExpression("0")
                                .build())
                .setMode(Field.Mode.REPEATED)
                .setDescription("Event attributes")
                .build();
        Field activeDates = Field.newBuilder("active_dates", StandardSQLTypeName.RANGE)
                .setRangeElementType(dateElementType)
                .build();
        Field email = Field.newBuilder("email", StandardSQLTypeName.STRING)
                .setMode(Field.Mode.REQUIRED)
                .setPolicyTags(policyTags)
                .build();
        Schema schema = Schema.of(email, eventAttributes, activeDates);

        FieldList fields = schema.getFields();
        assertThat(fields).hasSize(3);
        assertThat(fields.get("EMAIL")).isEqualTo(email);
        assertThat(fields.getIndex("attributes")).isEqualTo(1);
        assertThat(fields.get("attributes").getSubFields().get("source").getMaxLength()).isEqualTo(32L);
        assertThat(fields.get("attributes").getSubFields().get("score").getDefaultValueExpression()).isEqualTo("0");
        assertThat(fields.get("attributes").getMode()).isEqualTo(Field.Mode.REPEATED);
        assertThat(fields.get("active_dates").getRangeElementType()).isEqualTo(dateElementType);
        assertThat(fields.get("email").getPolicyTags().getNames()).containsExactly(policyTags.getNames().get(0));
        assertThat(email.toBuilder().setDescription("primary contact").build().getDescription())
                .isEqualTo("primary contact");
    }

    @Test
    void fieldValuesRepresentPrimitiveRepeatedRecordNullAndRangeResults() {
        FieldElementType dateElementType = FieldElementType.newBuilder().setType("DATE").build();
        Range activeRange = Range.newBuilder()
                .setType(dateElementType)
                .setStart("2024-01-01")
                .setEnd("2024-02-01")
                .build();
        FieldValue amount = FieldValue.of(FieldValue.Attribute.PRIMITIVE, "123.45");
        FieldValue active = FieldValue.of(FieldValue.Attribute.PRIMITIVE, "true");
        FieldValue tags = FieldValue.of(FieldValue.Attribute.REPEATED, List.of(
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "signup"),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, "paid")));
        FieldValue record = FieldValue.of(FieldValue.Attribute.RECORD, FieldValueList.of(
                List.of(amount, active, tags),
                Field.of("amount", StandardSQLTypeName.NUMERIC),
                Field.of("active", StandardSQLTypeName.BOOL),
                Field.newBuilder("tags", StandardSQLTypeName.STRING).setMode(Field.Mode.REPEATED).build()));
        FieldValue rangeValue = FieldValue.of(FieldValue.Attribute.RANGE, activeRange);
        FieldValue nullValue = FieldValue.of(FieldValue.Attribute.PRIMITIVE, null);

        assertThat(amount.getNumericValue()).isEqualByComparingTo(new BigDecimal("123.45"));
        assertThat(active.getBooleanValue()).isTrue();
        assertThat(tags.getRepeatedValue()).extracting(FieldValue::getStringValue).containsExactly("signup", "paid");
        assertThat(record.getRecordValue().hasSchema()).isTrue();
        assertThat(record.getRecordValue().get("amount").getNumericValue()).isEqualByComparingTo("123.45");
        assertThat(record.getRecordValue().get("tags").getRepeatedValue()).hasSize(2);
        assertThat(rangeValue.getRangeValue().getType().getType()).isEqualTo("DATE");
        assertThat(rangeValue.getRangeValue().getStart().getStringValue()).isEqualTo("2024-01-01");
        assertThat(rangeValue.getRangeValue().getEnd().getStringValue()).isEqualTo("2024-02-01");
        assertThat(nullValue.isNull()).isTrue();
        assertThat(nullValue.getStringValueOrDefault("fallback")).isEqualTo("fallback");
    }

    @Test
    void viewDefinitionsModelLogicalViewsMaterializedViewsRefreshAndTableMetadata() {
        Schema logicalViewSchema = Schema.of(
                Field.of("customer_id", StandardSQLTypeName.STRING),
                Field.of("event_count", StandardSQLTypeName.INT64));
        UserDefinedFunction formatter = UserDefinedFunction.inline(
                "function formatCustomer(value) { return value.toUpperCase(); }");
        ViewDefinition logicalView = ViewDefinition
                .newBuilder("SELECT formatCustomer(customer_id) AS customer_id, COUNT(*) AS event_count FROM events")
                .setSchema(logicalViewSchema)
                .setUseLegacySql(false)
                .setUserDefinedFunctions(formatter)
                .build();
        TableInfo logicalViewInfo = TableInfo.newBuilder(TableId.of(PROJECT, DATASET, "customer_event_summary"),
                        logicalView)
                .setFriendlyName("Customer event summary")
                .setDescription("Logical view over customer events")
                .build();

        TimePartitioning partitioning = TimePartitioning.newBuilder(TimePartitioning.Type.DAY)
                .setField("event_date")
                .build();
        Clustering clustering = Clustering.newBuilder().setFields(List.of("customer_id")).build();
        MaterializedViewDefinition materializedView = MaterializedViewDefinition
                .newBuilder("SELECT customer_id, event_date, COUNT(*) AS event_count FROM events GROUP BY 1, 2")
                .setSchema(Schema.of(
                        Field.of("customer_id", StandardSQLTypeName.STRING),
                        Field.of("event_date", StandardSQLTypeName.DATE),
                        Field.of("event_count", StandardSQLTypeName.INT64)))
                .setEnableRefresh(true)
                .setRefreshIntervalMs(3_600_000L)
                .setTimePartitioning(partitioning)
                .setClustering(clustering)
                .build();
        TableInfo materializedViewInfo = TableInfo.of(
                TableId.of(PROJECT, DATASET, "daily_customer_event_summary"), materializedView);

        assertThat(logicalView.getType()).isEqualTo(TableDefinition.Type.VIEW);
        assertThat(logicalView.getQuery()).contains("formatCustomer(customer_id)");
        assertThat(logicalView.useLegacySql()).isFalse();
        assertThat(logicalView.getSchema()).isEqualTo(logicalViewSchema);
        assertThat(logicalView.getUserDefinedFunctions()).containsExactly(formatter);
        assertThat(((ViewDefinition) logicalViewInfo.getDefinition()).getUserDefinedFunctions())
                .containsExactly(formatter);
        assertThat(logicalViewInfo.getFriendlyName()).isEqualTo("Customer event summary");
        assertThat(logicalView.toBuilder().setUseLegacySql(true).build().useLegacySql()).isTrue();
        assertThat(materializedView.getType()).isEqualTo(TableDefinition.Type.MATERIALIZED_VIEW);
        assertThat(materializedView.getQuery()).contains("GROUP BY 1, 2");
        assertThat(materializedView.getEnableRefresh()).isTrue();
        assertThat(materializedView.getRefreshIntervalMs()).isEqualTo(3_600_000L);
        assertThat(materializedView.getTimePartitioning()).isEqualTo(partitioning);
        assertThat(materializedView.getClustering().getFields()).containsExactly("customer_id");
        assertThat((MaterializedViewDefinition) materializedViewInfo.getDefinition()).isEqualTo(materializedView);
        assertThat(materializedView.toBuilder().setEnableRefresh(false).build().getEnableRefresh()).isFalse();
    }

    @Test
    void standardTableDefinitionCarriesPartitioningClusteringConstraintsAndTableMetadata() {
        Schema schema = Schema.of(
                Field.of("customer_id", StandardSQLTypeName.STRING),
                Field.of("event_date", StandardSQLTypeName.DATE),
                Field.of("bucket_id", StandardSQLTypeName.INT64));
        TimePartitioning timePartitioning = TimePartitioning.newBuilder(TimePartitioning.Type.DAY)
                .setField("event_date")
                .setExpirationMs(86_400_000L)
                .setRequirePartitionFilter(true)
                .build();
        RangePartitioning rangePartitioning = RangePartitioning.newBuilder()
                .setField("bucket_id")
                .setRange(RangePartitioning.Range.newBuilder()
                        .setStart(0L)
                        .setEnd(100L)
                        .setInterval(10L)
                        .build())
                .build();
        Clustering clustering = Clustering.newBuilder().setFields(List.of("customer_id", "event_date")).build();
        PrimaryKey primaryKey = PrimaryKey.newBuilder().setColumns(List.of("customer_id", "event_date")).build();
        ForeignKey foreignKey = ForeignKey.newBuilder()
                .setName("fk_customer")
                .setReferencedTable(TableId.of(PROJECT, DATASET, "customers"))
                .setColumnReferences(List.of(ColumnReference.newBuilder()
                        .setReferencingColumn("customer_id")
                        .setReferencedColumn("customer_id")
                        .build()))
                .build();
        TableConstraints constraints = TableConstraints.newBuilder()
                .setPrimaryKey(primaryKey)
                .setForeignKeys(List.of(foreignKey))
                .build();
        StandardTableDefinition definition = StandardTableDefinition.newBuilder()
                .setSchema(schema)
                .setLocation(LOCATION)
                .setNumRows(25L)
                .setNumBytes(2048L)
                .setTimePartitioning(timePartitioning)
                .setRangePartitioning(rangePartitioning)
                .setClustering(clustering)
                .setTableConstraints(constraints)
                .build();
        TableInfo tableInfo = TableInfo.newBuilder(TableId.of(PROJECT, DATASET, TABLE), definition)
                .setFriendlyName("Events")
                .setDescription("Event facts")
                .setExpirationTime(1_893_456_000_000L)
                .setRequirePartitionFilter(true)
                .setDefaultCollation("und:ci")
                .setLabels(Map.of("env", "test"))
                .setResourceTags(Map.of("tagKeys/123", "tagValues/456"))
                .build();

        assertThat(definition.getType()).isEqualTo(TableDefinition.Type.TABLE);
        assertThat(definition.getSchema()).isEqualTo(schema);
        assertThat(definition.getTimePartitioning().getRequirePartitionFilter()).isTrue();
        assertThat(definition.getRangePartitioning().getRange().getInterval()).isEqualTo(10L);
        assertThat(definition.getClustering().getFields()).containsExactly("customer_id", "event_date");
        assertThat(definition.getTableConstraints().getPrimaryKey().getColumns())
                .containsExactly("customer_id", "event_date");
        assertThat(definition.getTableConstraints().getForeignKeys().get(0).getColumnReferences().get(0)
                .getReferencedColumn()).isEqualTo("customer_id");
        assertThat((StandardTableDefinition) tableInfo.getDefinition()).isEqualTo(definition);
        assertThat(tableInfo.getFriendlyName()).isEqualTo("Events");
        assertThat(tableInfo.getLabels()).containsEntry("env", "test");
        assertThat(tableInfo.getResourceTags()).containsEntry("tagKeys/123", "tagValues/456");
        assertThat(tableInfo.getDefaultCollation()).isEqualTo("und:ci");
    }

    @Test
    void externalTableDefinitionCombinesCsvFormatHivePartitioningAndObjectMetadata() {
        CsvOptions csvOptions = CsvOptions.newBuilder()
                .setAllowJaggedRows(true)
                .setAllowQuotedNewLines(true)
                .setEncoding("UTF-8")
                .setFieldDelimiter("|")
                .setNullMarker("NULL")
                .setQuote("\"")
                .setSkipLeadingRows(1L)
                .setPreserveAsciiControlCharacters(true)
                .build();
        HivePartitioningOptions hiveOptions = HivePartitioningOptions.newBuilder()
                .setMode("CUSTOM")
                .setSourceUriPrefix("gs://bucket/events/{event_date:DATE}/{region:STRING}")
                .setRequirePartitionFilter(true)
                .setFields(List.of("event_date", "region"))
                .build();
        Schema schema = Schema.of(Field.of("payload", StandardSQLTypeName.JSON));
        ExternalTableDefinition definition = ExternalTableDefinition
                .newBuilder(List.of("gs://bucket/events/2024-01-01/us/file.csv"), schema, csvOptions)
                .setCompression("GZIP")
                .setConnectionId("projects/sample-project/locations/us/connections/events")
                .setMaxBadRecords(3)
                .setIgnoreUnknownValues(true)
                .setAutodetect(false)
                .setHivePartitioningOptions(hiveOptions)
                .setReferenceFileSchemaUri("gs://bucket/schema/reference.csv")
                .setDecimalTargetTypes(List.of("NUMERIC", "BIGNUMERIC"))
                .setObjectMetadata("DIRECTORY")
                .setMetadataCacheMode("AUTOMATIC")
                .setMaxStaleness("0-0 0 4:0:0")
                .build();
        CsvOptions parsedCsvOptions = definition.getFormatOptions();

        assertThat(parsedCsvOptions.allowJaggedRows()).isTrue();
        assertThat(parsedCsvOptions.getFieldDelimiter()).isEqualTo("|");
        assertThat(parsedCsvOptions.getPreserveAsciiControlCharacters()).isTrue();
        assertThat(definition.getSourceUris()).containsExactly("gs://bucket/events/2024-01-01/us/file.csv");
        assertThat(definition.getSchema()).isEqualTo(schema);
        assertThat(definition.getCompression()).isEqualTo("GZIP");
        assertThat(definition.getConnectionId()).endsWith("/connections/events");
        assertThat(definition.getMaxBadRecords()).isEqualTo(3);
        assertThat(definition.getIgnoreUnknownValues()).isTrue();
        assertThat(definition.getHivePartitioningOptions().getFields()).containsExactly("event_date", "region");
        assertThat(definition.getDecimalTargetTypes()).containsExactly("NUMERIC", "BIGNUMERIC");
        assertThat(definition.getObjectMetadata()).isEqualTo("DIRECTORY");
        assertThat(definition.getMetadataCacheMode()).isEqualTo("AUTOMATIC");
        assertThat(definition.getMaxStaleness()).isEqualTo("0-0 0 4:0:0");
        assertThat(FormatOptions.json().getType()).isEqualTo("NEWLINE_DELIMITED_JSON");
    }

    @Test
    void insertAllRequestPreservesRowsFlagsTemplateAndNestedContent() {
        Map<String, Object> firstRow = new LinkedHashMap<>();
        firstRow.put("customer_id", "customer-1");
        firstRow.put("score", 99);
        firstRow.put("attributes", Map.of("source", "mobile", "campaigns", List.of("spring", "vip")));
        InsertAllRequest.RowToInsert rowWithId = InsertAllRequest.RowToInsert.of("insert-1", firstRow);
        InsertAllRequest request = InsertAllRequest.newBuilder(TableId.of(PROJECT, DATASET, TABLE))
                .addRow(rowWithId)
                .addRow(Map.of("customer_id", "customer-2", "score", 75))
                .setIgnoreUnknownValues(true)
                .setSkipInvalidRows(true)
                .setTemplateSuffix("_202405")
                .build();

        assertThat(request.getTable()).isEqualTo(TableId.of(PROJECT, DATASET, TABLE));
        assertThat(request.getRows()).hasSize(2);
        assertThat(request.getRows().get(0).getId()).isEqualTo("insert-1");
        assertThat(request.getRows().get(0).getContent()).containsEntry("customer_id", "customer-1");
        assertThat(request.getRows().get(0).getContent().get("attributes")).isInstanceOf(Map.class);
        assertThat(request.getRows().get(1).getId()).isNull();
        assertThat(request.ignoreUnknownValues()).isTrue();
        assertThat(request.skipInvalidRows()).isTrue();
        assertThat(request.getTemplateSuffix()).isEqualTo("_202405");
    }

    @Test
    void queryConfigurationSupportsParametersExternalTablesUdfsAndSessions() {
        FieldElementType dateElementType = FieldElementType.newBuilder().setType("DATE").build();
        Range activeRange = Range.newBuilder()
                .setType(dateElementType)
                .setStart("2024-01-01")
                .setEnd("2024-02-01")
                .build();
        Map<String, QueryParameterValue> structFields = new LinkedHashMap<>();
        structFields.put("customer", QueryParameterValue.string("customer-1"));
        structFields.put("active", QueryParameterValue.bool(true));
        QueryParameterValue structParameter = QueryParameterValue.struct(structFields);
        ExternalTableDefinition externalTable = ExternalTableDefinition.of(
                "gs://bucket/events/*.json",
                Schema.of(Field.of("payload", StandardSQLTypeName.JSON)),
                FormatOptions.json());
        UserDefinedFunction inlineFunction = UserDefinedFunction.inline(
                "function normalize(value) { return value.toLowerCase(); }");
        ConnectionProperty sessionProperty = ConnectionProperty.of("session_id", "session-123");
        QueryJobConfiguration configuration = QueryJobConfiguration
                .newBuilder("SELECT normalize(@customer.customer) AS customer")
                .setUseLegacySql(false)
                .setDryRun(true)
                .setCreateSession(true)
                .setDefaultDataset(DatasetId.of(PROJECT, DATASET))
                .setDestinationTable(TableId.of(PROJECT, DATASET, "query_results"))
                .setPriority(QueryJobConfiguration.Priority.BATCH)
                .setUseQueryCache(false)
                .setMaximumBytesBilled(1_000_000L)
                .setMaxResults(50L)
                .setLabels(Map.of("component", "query"))
                .addNamedParameter("customer", structParameter)
                .addNamedParameter("scores", QueryParameterValue.array(new Long[] {10L, 20L}, Long.class))
                .addNamedParameter("active_range", QueryParameterValue.range(activeRange))
                .addTableDefinition("external_events", externalTable)
                .setUserDefinedFunctions(List.of(inlineFunction, UserDefinedFunction.fromUri("gs://bucket/lib.js")))
                .setConnectionProperties(List.of(sessionProperty))
                .setReservation("projects/sample-project/locations/us/reservations/test")
                .build();

        assertThat(configuration.getQuery()).contains("normalize(@customer.customer)");
        assertThat(configuration.useLegacySql()).isFalse();
        assertThat(configuration.dryRun()).isTrue();
        assertThat(configuration.createSession()).isTrue();
        assertThat(configuration.getDefaultDataset()).isEqualTo(DatasetId.of(PROJECT, DATASET));
        assertThat(configuration.getPriority()).isEqualTo(QueryJobConfiguration.Priority.BATCH);
        assertThat(configuration.getNamedParameters().get("customer").getStructValues())
                .containsKeys("customer", "active");
        assertThat(configuration.getNamedParameters().get("scores").getArrayValues())
                .extracting(QueryParameterValue::getValue)
                .containsExactly("10", "20");
        assertThat(configuration.getNamedParameters().get("active_range").getRangeValues().getStart().getStringValue())
                .isEqualTo("2024-01-01");
        assertThat(configuration.getTableDefinitions()).containsEntry("external_events", externalTable);
        assertThat(configuration.getUserDefinedFunctions()).extracting(UserDefinedFunction::getType)
                .containsExactly(UserDefinedFunction.Type.INLINE, UserDefinedFunction.Type.FROM_URI);
        assertThat(configuration.getConnectionProperties()).containsExactly(sessionProperty);
        assertThat(configuration.getReservation()).endsWith("/reservations/test");
    }

    @Test
    void loadCopyExtractWriteAndJobInfoConfigurationsExposeJobSettings() {
        TableId sourceTable = TableId.of(PROJECT, DATASET, "events_raw");
        TableId destinationTable = TableId.of(PROJECT, DATASET, TABLE);
        Schema schema = Schema.of(Field.of("payload", StandardSQLTypeName.JSON));
        CsvOptions csvOptions = CsvOptions.newBuilder().setSkipLeadingRows(1L).build();
        TimePartitioning partitioning = TimePartitioning.newBuilder(TimePartitioning.Type.DAY)
                .setField("event_date")
                .build();
        Clustering clustering = Clustering.newBuilder().setFields(List.of("customer_id")).build();
        LoadJobConfiguration load = LoadJobConfiguration
                .newBuilder(destinationTable, List.of("gs://bucket/events/file-1.csv", "gs://bucket/events/file-2.csv"),
                        csvOptions)
                .setSchema(schema)
                .setCreateDisposition(JobInfo.CreateDisposition.CREATE_IF_NEEDED)
                .setWriteDisposition(JobInfo.WriteDisposition.WRITE_APPEND)
                .setIgnoreUnknownValues(true)
                .setMaxBadRecords(2)
                .setAutodetect(false)
                .setTimePartitioning(partitioning)
                .setClustering(clustering)
                .setSchemaUpdateOptions(List.of(JobInfo.SchemaUpdateOption.ALLOW_FIELD_ADDITION))
                .setLabels(Map.of("job", "load"))
                .setJobTimeoutMs(30_000L)
                .setConnectionProperties(List.of(ConnectionProperty.of("time_zone", "UTC")))
                .build();
        CopyJobConfiguration copy = CopyJobConfiguration.newBuilder(destinationTable, List.of(sourceTable))
                .setCreateDisposition(JobInfo.CreateDisposition.CREATE_NEVER)
                .setWriteDisposition(JobInfo.WriteDisposition.WRITE_TRUNCATE)
                .setLabels(Map.of("job", "copy"))
                .setJobTimeoutMs(30_000L)
                .build();
        ExtractJobConfiguration extract = ExtractJobConfiguration
                .newBuilder(destinationTable, List.of("gs://bucket/export/events-*.avro"))
                .setFormat("AVRO")
                .setCompression("SNAPPY")
                .setUseAvroLogicalTypes(true)
                .setLabels(Map.of("job", "extract"))
                .build();
        JobInfo jobInfo = JobInfo.of(
                JobId.newBuilder().setProject(PROJECT).setLocation(LOCATION).setJob("load-job").build(),
                load);

        assertThat(load.getDestinationTable()).isEqualTo(destinationTable);
        assertThat(load.getSourceUris()).containsExactly("gs://bucket/events/file-1.csv", "gs://bucket/events/file-2.csv");
        assertThat(load.getCsvOptions().getSkipLeadingRows()).isEqualTo(1L);
        assertThat(load.getSchema()).isEqualTo(schema);
        assertThat(load.ignoreUnknownValues()).isTrue();
        assertThat(load.getSchemaUpdateOptions()).containsExactly(JobInfo.SchemaUpdateOption.ALLOW_FIELD_ADDITION);
        assertThat(load.getTimePartitioning()).isEqualTo(partitioning);
        assertThat(load.getClustering()).isEqualTo(clustering);
        assertThat(load.getConnectionProperties()).extracting(ConnectionProperty::getKey).containsExactly("time_zone");
        assertThat(copy.getDestinationTable()).isEqualTo(destinationTable);
        assertThat(copy.getSourceTables()).containsExactly(sourceTable);
        assertThat(copy.getWriteDisposition()).isEqualTo(JobInfo.WriteDisposition.WRITE_TRUNCATE);
        assertThat(extract.getSourceTable()).isEqualTo(destinationTable);
        assertThat(extract.getDestinationUris()).containsExactly("gs://bucket/export/events-*.avro");
        assertThat(extract.getFormat()).isEqualTo("AVRO");
        assertThat(extract.getUseAvroLogicalTypes()).isTrue();
        assertThat(jobInfo.getJobId().getLocation()).isEqualTo(LOCATION);
        assertThat((LoadJobConfiguration) jobInfo.getConfiguration()).isEqualTo(load);
    }

    @Test
    void routineInfoModelsSqlArgumentsStructReturnTypeAndRemoteFunctionOptions() {
        StandardSQLDataType stringType = StandardSQLDataType.newBuilder(StandardSQLTypeName.STRING).build();
        StandardSQLDataType intType = StandardSQLDataType.newBuilder(StandardSQLTypeName.INT64).build();
        StandardSQLStructType structType = StandardSQLStructType.newBuilder(List.of(
                        StandardSQLField.newBuilder("normalized", stringType).build(),
                        StandardSQLField.newBuilder("score", intType).build()))
                .build();
        StandardSQLDataType returnType = StandardSQLDataType.newBuilder(StandardSQLTypeName.STRUCT)
                .setStructType(structType)
                .build();
        RoutineArgument argument = RoutineArgument.newBuilder()
                .setName("event_name")
                .setKind("FIXED_TYPE")
                .setMode("IN")
                .setDataType(stringType)
                .build();
        RemoteFunctionOptions remoteFunctionOptions = RemoteFunctionOptions.newBuilder()
                .setEndpoint("https://example.com/bigquery/normalize")
                .setConnection("projects/sample-project/locations/us/connections/remote-functions")
                .setUserDefinedContext(Map.of("service", "normalizer"))
                .setMaxBatchingRows(100L)
                .build();
        RoutineInfo routineInfo = RoutineInfo.newBuilder(RoutineId.of(PROJECT, DATASET, "normalize_event"))
                .setRoutineType("SCALAR_FUNCTION")
                .setLanguage("SQL")
                .setDeterminismLevel("DETERMINISTIC")
                .setArguments(List.of(argument))
                .setReturnType(returnType)
                .setImportedLibraries(List.of("gs://bucket/lib/normalize.js"))
                .setBody("SELECT STRUCT(LOWER(event_name) AS normalized, 1 AS score)")
                .setRemoteFunctionOptions(remoteFunctionOptions)
                .setDataGovernanceType("DATA_MASKING")
                .build();

        assertThat(routineInfo.getRoutineId()).isEqualTo(RoutineId.of(PROJECT, DATASET, "normalize_event"));
        assertThat(routineInfo.getRoutineType()).isEqualTo("SCALAR_FUNCTION");
        assertThat(routineInfo.getLanguage()).isEqualTo("SQL");
        assertThat(routineInfo.getArguments()).containsExactly(argument);
        assertThat(routineInfo.getArguments().get(0).getDataType().getTypeKind()).isEqualTo("STRING");
        assertThat(routineInfo.getReturnType().getStructType().getFields()).extracting(StandardSQLField::getName)
                .containsExactly("normalized", "score");
        assertThat(routineInfo.getRemoteFunctionOptions().getUserDefinedContext())
                .containsEntry("service", "normalizer");
        assertThat(routineInfo.getRemoteFunctionOptions().getMaxBatchingRows()).isEqualTo(100L);
        assertThat(routineInfo.getDataGovernanceType()).isEqualTo("DATA_MASKING");
        assertThat(routineInfo.toBuilder().setBody("SELECT STRUCT(event_name AS normalized, 0 AS score)").build()
                .getBody()).contains("STRUCT");
    }
}
