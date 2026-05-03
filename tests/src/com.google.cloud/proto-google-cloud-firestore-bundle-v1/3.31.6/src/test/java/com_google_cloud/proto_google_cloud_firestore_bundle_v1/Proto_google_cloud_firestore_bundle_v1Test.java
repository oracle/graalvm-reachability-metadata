/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.proto_google_cloud_firestore_bundle_v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.firestore.bundle.BundleElement;
import com.google.firestore.bundle.BundleMetadata;
import com.google.firestore.bundle.BundleProto;
import com.google.firestore.bundle.BundledDocumentMetadata;
import com.google.firestore.bundle.BundledQuery;
import com.google.firestore.bundle.NamedQuery;
import com.google.firestore.v1.Cursor;
import com.google.firestore.v1.Document;
import com.google.firestore.v1.StructuredQuery;
import com.google.firestore.v1.Value;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Int32Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

public class Proto_google_cloud_firestore_bundle_v1Test {
    private static final String PARENT = "projects/test-project/databases/(default)/documents";
    private static final String DOCUMENT_NAME = PARENT + "/cities/SF";

    @Test
    void fileDescriptorExposesFirestoreBundleSchema() {
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        BundleProto.registerAllExtensions(registry);

        Descriptors.FileDescriptor descriptor = BundleProto.getDescriptor();

        assertThat(descriptor.getPackage()).isEqualTo("google.firestore.bundle");
        assertThat(descriptor.getMessageTypes()).extracting(Descriptors.Descriptor::getName)
                .containsExactly(
                        "BundledQuery", "NamedQuery", "BundledDocumentMetadata", "BundleMetadata", "BundleElement");
        assertThat(BundleElement.getDescriptor().getOneofs()).extracting(Descriptors.OneofDescriptor::getName)
                .containsExactly("element_type");
        assertThat(BundledQuery.getDescriptor().getOneofs()).extracting(Descriptors.OneofDescriptor::getName)
                .containsExactly("query_type");
        assertThat(BundleMetadata.getDescriptor().findFieldByNumber(BundleMetadata.TOTAL_BYTES_FIELD_NUMBER).getName())
                .isEqualTo("total_bytes");
    }

    @Test
    void bundleMetadataRoundTripsThroughBinaryRepresentations() throws Exception {
        BundleMetadata metadata = BundleMetadata.newBuilder()
                .setId("bundle-cities")
                .setCreateTime(timestamp(1_700_000_000L, 123_000_000))
                .setVersion(1)
                .setTotalDocuments(2)
                .setTotalBytes(4096L)
                .build();

        BundleMetadata parsedFromBytes = BundleMetadata.parseFrom(metadata.toByteArray());
        BundleMetadata parsedFromByteString = BundleMetadata.parseFrom(metadata.toByteString());
        BundleMetadata parsedFromBuffer = BundleMetadata.parseFrom(ByteBuffer.wrap(metadata.toByteArray()));
        BundleMetadata parsedFromParser = BundleMetadata.parser().parseFrom(metadata.toByteString());

        assertThat(List.of(parsedFromBytes, parsedFromByteString, parsedFromBuffer, parsedFromParser))
                .containsOnly(metadata);
        assertThat(parsedFromBytes.hasCreateTime()).isTrue();
        assertThat(parsedFromBytes.getIdBytes()).isEqualTo(ByteString.copyFromUtf8("bundle-cities"));
        assertThat(parsedFromBytes.getTotalBytes()).isEqualTo(4096L);
        assertThat(parsedFromBytes.isInitialized()).isTrue();
        assertThat(BundleMetadata.newBuilder(parsedFromBytes)
                .setTotalDocuments(3)
                .build()
                .getTotalDocuments()).isEqualTo(3);
    }

    @Test
    void namedQueryRetainsStructuredQueryAndReadTime() throws Exception {
        StructuredQuery structuredQuery = StructuredQuery.newBuilder()
                .addFrom(StructuredQuery.CollectionSelector.newBuilder()
                        .setCollectionId("cities")
                        .setAllDescendants(false))
                .addOrderBy(StructuredQuery.Order.newBuilder()
                        .setField(StructuredQuery.FieldReference.newBuilder().setFieldPath("name"))
                        .setDirection(StructuredQuery.Direction.ASCENDING))
                .setLimit(Int32Value.of(10))
                .build();
        BundledQuery bundledQuery = BundledQuery.newBuilder()
                .setParent(PARENT)
                .setStructuredQuery(structuredQuery)
                .setLimitType(BundledQuery.LimitType.LAST)
                .build();
        NamedQuery namedQuery = NamedQuery.newBuilder()
                .setName("top-cities")
                .setBundledQuery(bundledQuery)
                .setReadTime(timestamp(1_700_000_100L, 0))
                .build();

        NamedQuery parsed = NamedQuery.parseFrom(new ByteArrayInputStream(namedQuery.toByteArray()));

        assertThat(parsed.getName()).isEqualTo("top-cities");
        assertThat(parsed.hasBundledQuery()).isTrue();
        assertThat(parsed.hasReadTime()).isTrue();
        assertThat(parsed.getBundledQuery().getParent()).isEqualTo(PARENT);
        assertThat(parsed.getBundledQuery().getQueryTypeCase()).isEqualTo(BundledQuery.QueryTypeCase.STRUCTURED_QUERY);
        assertThat(parsed.getBundledQuery().getLimitType()).isEqualTo(BundledQuery.LimitType.LAST);
        assertThat(parsed.getBundledQuery().getStructuredQuery().getFrom(0).getCollectionId()).isEqualTo("cities");
        assertThat(parsed.getBundledQuery()
                .getStructuredQuery()
                .getOrderBy(0)
                .getField()
                .getFieldPath()).isEqualTo("name");
        assertThat(parsed.getBundledQuery().getStructuredQuery().getLimit().getValue()).isEqualTo(10);
    }

    @Test
    void bundledQueryPreservesStructuredQueryFiltersAndCursors() {
        StructuredQuery.Filter stateFilter = StructuredQuery.Filter.newBuilder()
                .setFieldFilter(StructuredQuery.FieldFilter.newBuilder()
                        .setField(fieldReference("state"))
                        .setOp(StructuredQuery.FieldFilter.Operator.EQUAL)
                        .setValue(stringValue("CA")))
                .build();
        StructuredQuery.Filter populationFilter = StructuredQuery.Filter.newBuilder()
                .setFieldFilter(StructuredQuery.FieldFilter.newBuilder()
                        .setField(fieldReference("population"))
                        .setOp(StructuredQuery.FieldFilter.Operator.GREATER_THAN_OR_EQUAL)
                        .setValue(integerValue(500_000L)))
                .build();
        StructuredQuery structuredQuery = StructuredQuery.newBuilder()
                .setSelect(StructuredQuery.Projection.newBuilder()
                        .addFields(fieldReference("name"))
                        .addFields(fieldReference("population")))
                .addFrom(StructuredQuery.CollectionSelector.newBuilder()
                        .setCollectionId("cities")
                        .setAllDescendants(true))
                .setWhere(StructuredQuery.Filter.newBuilder()
                        .setCompositeFilter(StructuredQuery.CompositeFilter.newBuilder()
                                .setOp(StructuredQuery.CompositeFilter.Operator.AND)
                                .addFilters(stateFilter)
                                .addFilters(populationFilter)))
                .setStartAt(Cursor.newBuilder()
                        .addValues(integerValue(500_000L))
                        .setBefore(false))
                .setEndAt(Cursor.newBuilder()
                        .addValues(integerValue(1_000_000L))
                        .setBefore(true))
                .setOffset(2)
                .build();
        BundledQuery bundledQuery = BundledQuery.newBuilder()
                .setParent(PARENT)
                .setStructuredQuery(structuredQuery)
                .build();

        StructuredQuery storedQuery = bundledQuery.getStructuredQuery();
        StructuredQuery.CompositeFilter storedFilter = storedQuery.getWhere().getCompositeFilter();

        assertThat(bundledQuery.getQueryTypeCase()).isEqualTo(BundledQuery.QueryTypeCase.STRUCTURED_QUERY);
        assertThat(storedQuery.getSelect().getFieldsList())
                .extracting(StructuredQuery.FieldReference::getFieldPath)
                .containsExactly("name", "population");
        assertThat(storedQuery.getFrom(0).getCollectionId()).isEqualTo("cities");
        assertThat(storedQuery.getFrom(0).getAllDescendants()).isTrue();
        assertThat(storedFilter.getOp()).isEqualTo(StructuredQuery.CompositeFilter.Operator.AND);
        assertThat(storedFilter.getFiltersList())
                .extracting(filter -> filter.getFieldFilter().getField().getFieldPath())
                .containsExactly("state", "population");
        assertThat(storedFilter.getFilters(0).getFieldFilter().getOp())
                .isEqualTo(StructuredQuery.FieldFilter.Operator.EQUAL);
        assertThat(storedFilter.getFilters(0).getFieldFilter().getValue().getStringValue()).isEqualTo("CA");
        assertThat(storedFilter.getFilters(1).getFieldFilter().getOp())
                .isEqualTo(StructuredQuery.FieldFilter.Operator.GREATER_THAN_OR_EQUAL);
        assertThat(storedFilter.getFilters(1).getFieldFilter().getValue().getIntegerValue()).isEqualTo(500_000L);
        assertThat(storedQuery.getStartAt().getValues(0).getIntegerValue()).isEqualTo(500_000L);
        assertThat(storedQuery.getStartAt().getBefore()).isFalse();
        assertThat(storedQuery.getEndAt().getValues(0).getIntegerValue()).isEqualTo(1_000_000L);
        assertThat(storedQuery.getEndAt().getBefore()).isTrue();
        assertThat(storedQuery.getOffset()).isEqualTo(2);
    }

    @Test
    void bundledQueryPreservesUnrecognizedLimitTypeValues() throws Exception {
        StructuredQuery structuredQuery = StructuredQuery.newBuilder()
                .addFrom(StructuredQuery.CollectionSelector.newBuilder().setCollectionId("cities"))
                .build();
        BundledQuery bundledQuery = BundledQuery.newBuilder()
                .setParent(PARENT)
                .setStructuredQuery(structuredQuery)
                .setLimitTypeValue(99)
                .build();

        BundledQuery parsed = BundledQuery.parseFrom(bundledQuery.toByteArray());
        BundledQuery normalized = parsed.toBuilder()
                .setLimitType(BundledQuery.LimitType.FIRST)
                .build();

        assertThat(parsed.getLimitTypeValue()).isEqualTo(99);
        assertThat(parsed.getLimitType()).isEqualTo(BundledQuery.LimitType.UNRECOGNIZED);
        assertThat(parsed.getQueryTypeCase()).isEqualTo(BundledQuery.QueryTypeCase.STRUCTURED_QUERY);
        assertThat(parsed.getStructuredQuery().getFrom(0).getCollectionId()).isEqualTo("cities");
        assertThat(normalized.getLimitTypeValue()).isEqualTo(BundledQuery.LimitType.FIRST.getNumber());
        assertThat(normalized.getLimitType()).isEqualTo(BundledQuery.LimitType.FIRST);
    }

    @Test
    void bundledDocumentMetadataKeepsRepeatedQueryMembership() throws Exception {
        BundledDocumentMetadata documentMetadata = BundledDocumentMetadata.newBuilder()
                .setName(DOCUMENT_NAME)
                .setReadTime(timestamp(1_700_000_200L, 987_000_000))
                .setExists(true)
                .addAllQueries(List.of("top-cities", "california-cities"))
                .setQueries(1, "west-coast-cities")
                .addQueriesBytes(ByteString.copyFromUtf8("large-cities"))
                .build();

        BundledDocumentMetadata parsed = BundledDocumentMetadata.parseFrom(documentMetadata.toByteString());
        BundledDocumentMetadata withoutReadTime = parsed.toBuilder().clearReadTime().clearExists().build();

        assertThat(parsed.getName()).isEqualTo(DOCUMENT_NAME);
        assertThat(parsed.hasReadTime()).isTrue();
        assertThat(parsed.getExists()).isTrue();
        assertThat(parsed.getQueriesList()).containsExactly("top-cities", "west-coast-cities", "large-cities");
        assertThat(parsed.getQueriesBytes(2)).isEqualTo(ByteString.copyFromUtf8("large-cities"));
        assertThat(withoutReadTime.hasReadTime()).isFalse();
        assertThat(withoutReadTime.getExists()).isFalse();
    }

    @Test
    void bundleElementOneofSwitchesBetweenSupportedElementTypes() {
        BundleMetadata metadata = sampleMetadata();
        NamedQuery namedQuery = sampleNamedQuery();
        BundledDocumentMetadata documentMetadata = sampleDocumentMetadata();
        Document document = sampleDocument();

        BundleElement.Builder builder = BundleElement.newBuilder().setMetadata(metadata);
        assertThat(builder.build().getElementTypeCase()).isEqualTo(BundleElement.ElementTypeCase.METADATA);

        BundleElement namedQueryElement = builder.setNamedQuery(namedQuery).build();
        assertThat(namedQueryElement.hasMetadata()).isFalse();
        assertThat(namedQueryElement.hasNamedQuery()).isTrue();
        assertThat(namedQueryElement.getElementTypeCase()).isEqualTo(BundleElement.ElementTypeCase.NAMED_QUERY);

        BundleElement documentMetadataElement = namedQueryElement.toBuilder()
                .setDocumentMetadata(documentMetadata)
                .build();
        assertThat(documentMetadataElement.hasNamedQuery()).isFalse();
        assertThat(documentMetadataElement.hasDocumentMetadata()).isTrue();
        assertThat(documentMetadataElement.getElementTypeCase())
                .isEqualTo(BundleElement.ElementTypeCase.DOCUMENT_METADATA);

        BundleElement documentElement = documentMetadataElement.toBuilder().setDocument(document).build();
        assertThat(documentElement.hasDocumentMetadata()).isFalse();
        assertThat(documentElement.hasDocument()).isTrue();
        assertThat(documentElement.getElementTypeCase()).isEqualTo(BundleElement.ElementTypeCase.DOCUMENT);
        assertThat(documentElement.toBuilder().clearElementType().build().getElementTypeCase())
                .isEqualTo(BundleElement.ElementTypeCase.ELEMENTTYPE_NOT_SET);
    }

    @Test
    void delimitedBundleElementStreamReadsElementsInOrder() throws Exception {
        BundleElement metadataElement = BundleElement.newBuilder().setMetadata(sampleMetadata()).build();
        BundleElement queryElement = BundleElement.newBuilder().setNamedQuery(sampleNamedQuery()).build();
        BundleElement documentMetadataElement = BundleElement.newBuilder()
                .setDocumentMetadata(sampleDocumentMetadata())
                .build();
        BundleElement documentElement = BundleElement.newBuilder().setDocument(sampleDocument()).build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        metadataElement.writeDelimitedTo(output);
        queryElement.writeDelimitedTo(output);
        documentMetadataElement.writeDelimitedTo(output);
        documentElement.writeDelimitedTo(output);

        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());

        assertThat(BundleElement.parseDelimitedFrom(input)).isEqualTo(metadataElement);
        assertThat(BundleElement.parseDelimitedFrom(input)).isEqualTo(queryElement);
        assertThat(BundleElement.parseDelimitedFrom(input)).isEqualTo(documentMetadataElement);
        assertThat(BundleElement.parseDelimitedFrom(input)).isEqualTo(documentElement);
        assertThat(BundleElement.parseDelimitedFrom(input)).isNull();
    }

    @Test
    void invalidBundleElementPayloadRaisesProtocolBufferException() {
        byte[] invalidPayload = {(byte) 0x80, (byte) 0x80, (byte) 0x80};

        assertThatThrownBy(() -> BundleElement.parseFrom(invalidPayload))
                .isInstanceOf(InvalidProtocolBufferException.class);
    }

    private static BundleMetadata sampleMetadata() {
        return BundleMetadata.newBuilder()
                .setId("bundle-cities")
                .setCreateTime(timestamp(1_700_000_000L, 0))
                .setVersion(1)
                .setTotalDocuments(1)
                .setTotalBytes(256L)
                .build();
    }

    private static NamedQuery sampleNamedQuery() {
        StructuredQuery structuredQuery = StructuredQuery.newBuilder()
                .addFrom(StructuredQuery.CollectionSelector.newBuilder().setCollectionId("cities"))
                .build();
        BundledQuery bundledQuery = BundledQuery.newBuilder()
                .setParent(PARENT)
                .setStructuredQuery(structuredQuery)
                .setLimitType(BundledQuery.LimitType.FIRST)
                .build();
        return NamedQuery.newBuilder()
                .setName("top-cities")
                .setBundledQuery(bundledQuery)
                .setReadTime(timestamp(1_700_000_100L, 0))
                .build();
    }

    private static BundledDocumentMetadata sampleDocumentMetadata() {
        return BundledDocumentMetadata.newBuilder()
                .setName(DOCUMENT_NAME)
                .setReadTime(timestamp(1_700_000_200L, 0))
                .setExists(true)
                .addQueries("top-cities")
                .build();
    }

    private static Document sampleDocument() {
        return Document.newBuilder()
                .setName(DOCUMENT_NAME)
                .putFields("name", Value.newBuilder().setStringValue("San Francisco").build())
                .putFields("population", Value.newBuilder().setIntegerValue(808_437L).build())
                .setCreateTime(timestamp(1_600_000_000L, 0))
                .setUpdateTime(timestamp(1_700_000_200L, 0))
                .build();
    }

    private static StructuredQuery.FieldReference fieldReference(String fieldPath) {
        return StructuredQuery.FieldReference.newBuilder().setFieldPath(fieldPath).build();
    }

    private static Value stringValue(String value) {
        return Value.newBuilder().setStringValue(value).build();
    }

    private static Value integerValue(long value) {
        return Value.newBuilder().setIntegerValue(value).build();
    }

    private static Timestamp timestamp(long seconds, int nanos) {
        return Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
    }
}
