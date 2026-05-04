/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.proto_google_cloud_firestore_bundle_v1;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firestore.bundle.BundleElement;
import com.google.firestore.bundle.BundleMetadata;
import com.google.firestore.bundle.BundleProto;
import com.google.firestore.bundle.BundledDocumentMetadata;
import com.google.firestore.bundle.BundledQuery;
import com.google.firestore.bundle.NamedQuery;
import com.google.firestore.v1.Document;
import com.google.firestore.v1.StructuredQuery;
import com.google.firestore.v1.Value;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UnknownFieldSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

public class Proto_google_cloud_firestore_bundle_v1Test {
    private static final String DATABASE = "projects/example-project/databases/(default)";
    private static final String DOCUMENT_NAME = DATABASE + "/documents/users/alice";

    @Test
    void buildsMetadataMessagesWithTimestampsRepeatedFieldsAndByteStringSetters() throws Exception {
        Timestamp createTime = timestamp(1_700_000_000L, 123_000_000);
        BundleMetadata metadata = BundleMetadata.newBuilder()
                .setIdBytes(ByteString.copyFromUtf8("bundle-users"))
                .setCreateTime(createTime)
                .setVersion(1)
                .setTotalDocuments(2)
                .setTotalBytes(4096L)
                .build();

        BundleMetadata parsedMetadata = BundleMetadata.parseFrom(
                metadata.toByteArray(), ExtensionRegistryLite.getEmptyRegistry());

        assertThat(parsedMetadata).isEqualTo(metadata);
        assertThat(parsedMetadata.getId()).isEqualTo("bundle-users");
        assertThat(parsedMetadata.getCreateTime()).isEqualTo(createTime);
        assertThat(parsedMetadata.getVersion()).isOne();
        assertThat(parsedMetadata.getTotalDocuments()).isEqualTo(2);
        assertThat(parsedMetadata.getTotalBytes()).isEqualTo(4096L);

        BundledDocumentMetadata documentMetadata = BundledDocumentMetadata.newBuilder()
                .setNameBytes(ByteString.copyFromUtf8(DOCUMENT_NAME))
                .setReadTime(timestamp(1_700_000_100L, 0))
                .setExists(true)
                .addQueries("all-users")
                .addQueriesBytes(ByteString.copyFromUtf8("active-users"))
                .build();

        BundledDocumentMetadata modifiedDocumentMetadata = documentMetadata.toBuilder()
                .setQueries(1, "recently-active-users")
                .addAllQueries(List.of("premium-users"))
                .build();

        assertThat(modifiedDocumentMetadata.getName()).isEqualTo(DOCUMENT_NAME);
        assertThat(modifiedDocumentMetadata.getExists()).isTrue();
        assertThat(modifiedDocumentMetadata.getQueriesList())
                .containsExactly("all-users", "recently-active-users", "premium-users");
        assertThat(BundledDocumentMetadata.parseFrom(modifiedDocumentMetadata.toByteString()))
                .isEqualTo(modifiedDocumentMetadata);
    }

    @Test
    void buildsNamedQueryWithStructuredFirestoreQueryAndOneofState() throws Exception {
        StructuredQuery structuredQuery = StructuredQuery.newBuilder()
                .addFrom(StructuredQuery.CollectionSelector.newBuilder()
                        .setCollectionId("users")
                        .setAllDescendants(true))
                .setWhere(StructuredQuery.Filter.newBuilder()
                        .setFieldFilter(StructuredQuery.FieldFilter.newBuilder()
                                .setField(StructuredQuery.FieldReference.newBuilder().setFieldPath("status"))
                                .setOp(StructuredQuery.FieldFilter.Operator.EQUAL)
                                .setValue(Value.newBuilder().setStringValue("active"))))
                .addOrderBy(StructuredQuery.Order.newBuilder()
                        .setField(StructuredQuery.FieldReference.newBuilder().setFieldPath("createdAt"))
                        .setDirection(StructuredQuery.Direction.DESCENDING))
                .setLimit(Int32Value.of(10))
                .build();

        BundledQuery bundledQuery = BundledQuery.newBuilder()
                .setParent(DATABASE + "/documents")
                .setStructuredQuery(structuredQuery)
                .setLimitType(BundledQuery.LimitType.LAST)
                .build();

        NamedQuery namedQuery = NamedQuery.newBuilder()
                .setName("latest-active-users")
                .setBundledQuery(bundledQuery)
                .setReadTime(timestamp(1_700_000_200L, 42))
                .build();

        assertThat(bundledQuery.hasStructuredQuery()).isTrue();
        assertThat(bundledQuery.getQueryTypeCase()).isEqualTo(BundledQuery.QueryTypeCase.STRUCTURED_QUERY);
        assertThat(bundledQuery.getLimitType()).isEqualTo(BundledQuery.LimitType.LAST);
        assertThat(namedQuery.hasBundledQuery()).isTrue();
        assertThat(namedQuery.getBundledQuery().getStructuredQuery().getFrom(0).getCollectionId()).isEqualTo("users");
        String filterValue = namedQuery.getBundledQuery().getStructuredQuery().getWhere().getFieldFilter()
                .getValue().getStringValue();
        assertThat(filterValue).isEqualTo("active");
        assertThat(namedQuery.getBundledQuery().getStructuredQuery().getOrderBy(0).getDirection())
                .isEqualTo(StructuredQuery.Direction.DESCENDING);

        NamedQuery parsedFromBuffer = NamedQuery.parseFrom(ByteBuffer.wrap(namedQuery.toByteArray()));
        assertThat(parsedFromBuffer).isEqualTo(namedQuery);

        BundledQuery clearedQuery = bundledQuery.toBuilder().clearStructuredQuery().clearLimitType().build();
        assertThat(clearedQuery.getQueryTypeCase()).isEqualTo(BundledQuery.QueryTypeCase.QUERYTYPE_NOT_SET);
        assertThat(clearedQuery.getLimitType()).isEqualTo(BundledQuery.LimitType.FIRST);
    }

    @Test
    void bundledQueryPreservesForwardCompatibleLimitTypeValues() throws Exception {
        int unrecognizedLimitTypeValue = 9876;
        BundledQuery query = BundledQuery.newBuilder()
                .setParent(DATABASE + "/documents")
                .setLimitTypeValue(unrecognizedLimitTypeValue)
                .build();

        assertThat(query.getLimitTypeValue()).isEqualTo(unrecognizedLimitTypeValue);
        assertThat(query.getLimitType()).isEqualTo(BundledQuery.LimitType.UNRECOGNIZED);

        BundledQuery parsedQuery = BundledQuery.parseFrom(query.toByteArray());
        assertThat(parsedQuery.getLimitTypeValue()).isEqualTo(unrecognizedLimitTypeValue);
        assertThat(parsedQuery.getLimitType()).isEqualTo(BundledQuery.LimitType.UNRECOGNIZED);

        BundledQuery recognizedQuery = parsedQuery.toBuilder()
                .setLimitType(BundledQuery.LimitType.LAST)
                .build();
        assertThat(recognizedQuery.getLimitTypeValue()).isEqualTo(BundledQuery.LimitType.LAST_VALUE);
        assertThat(recognizedQuery.getLimitType()).isEqualTo(BundledQuery.LimitType.LAST);
    }

    @Test
    void bundleElementSwitchesAmongEverySupportedElementType() throws Exception {
        BundleMetadata metadata = BundleMetadata.newBuilder()
                .setId("bundle-users")
                .setCreateTime(timestamp(1_700_000_000L, 0))
                .setVersion(1)
                .build();
        NamedQuery namedQuery = NamedQuery.newBuilder()
                .setName("all-users")
                .setBundledQuery(BundledQuery.newBuilder().setParent(DATABASE + "/documents"))
                .setReadTime(timestamp(1_700_000_010L, 0))
                .build();
        BundledDocumentMetadata documentMetadata = BundledDocumentMetadata.newBuilder()
                .setName(DOCUMENT_NAME)
                .setReadTime(timestamp(1_700_000_020L, 0))
                .setExists(true)
                .addQueries("all-users")
                .build();
        Document document = Document.newBuilder()
                .setName(DOCUMENT_NAME)
                .putFields("name", Value.newBuilder().setStringValue("Alice").build())
                .putFields("visits", Value.newBuilder().setIntegerValue(7L).build())
                .setCreateTime(timestamp(1_700_000_030L, 0))
                .setUpdateTime(timestamp(1_700_000_040L, 0))
                .build();

        BundleElement metadataElement = BundleElement.newBuilder().setMetadata(metadata).build();
        BundleElement namedQueryElement = metadataElement.toBuilder().setNamedQuery(namedQuery).build();
        BundleElement documentMetadataElement = namedQueryElement.toBuilder()
                .setDocumentMetadata(documentMetadata)
                .build();
        BundleElement documentElement = documentMetadataElement.toBuilder().setDocument(document).build();

        assertThat(metadataElement.getElementTypeCase()).isEqualTo(BundleElement.ElementTypeCase.METADATA);
        assertThat(namedQueryElement.getElementTypeCase()).isEqualTo(BundleElement.ElementTypeCase.NAMED_QUERY);
        assertThat(namedQueryElement.hasMetadata()).isFalse();
        assertThat(documentMetadataElement.getElementTypeCase())
                .isEqualTo(BundleElement.ElementTypeCase.DOCUMENT_METADATA);
        assertThat(documentElement.getElementTypeCase()).isEqualTo(BundleElement.ElementTypeCase.DOCUMENT);
        assertThat(documentElement.getDocument().getFieldsOrThrow("name").getStringValue()).isEqualTo("Alice");
        assertThat(documentElement.getDocument().getFieldsOrThrow("visits").getIntegerValue()).isEqualTo(7L);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        documentElement.writeDelimitedTo(output);
        BundleElement parsedDelimited = BundleElement.parseDelimitedFrom(
                new ByteArrayInputStream(output.toByteArray()));
        assertThat(parsedDelimited).isEqualTo(documentElement);

        BundleElement clearedElement = documentElement.toBuilder().clearElementType().build();
        assertThat(clearedElement.getElementTypeCase()).isEqualTo(BundleElement.ElementTypeCase.ELEMENTTYPE_NOT_SET);
        assertThat(clearedElement.hasDocument()).isFalse();
    }

    @Test
    void readsOrderedLengthPrefixedBundleElementStream() throws Exception {
        BundleElement metadataElement = BundleElement.newBuilder()
                .setMetadata(BundleMetadata.newBuilder()
                        .setId("ordered-bundle")
                        .setCreateTime(timestamp(1_700_000_050L, 0))
                        .setVersion(1)
                        .setTotalDocuments(1)
                        .setTotalBytes(128L))
                .build();
        BundleElement namedQueryElement = BundleElement.newBuilder()
                .setNamedQuery(NamedQuery.newBuilder()
                        .setName("ordered-users")
                        .setBundledQuery(BundledQuery.newBuilder()
                                .setParent(DATABASE + "/documents")
                                .setLimitType(BundledQuery.LimitType.FIRST))
                        .setReadTime(timestamp(1_700_000_060L, 0)))
                .build();
        BundleElement documentMetadataElement = BundleElement.newBuilder()
                .setDocumentMetadata(BundledDocumentMetadata.newBuilder()
                        .setName(DOCUMENT_NAME)
                        .setReadTime(timestamp(1_700_000_070L, 0))
                        .setExists(true)
                        .addQueries("ordered-users"))
                .build();
        BundleElement documentElement = BundleElement.newBuilder()
                .setDocument(Document.newBuilder()
                        .setName(DOCUMENT_NAME)
                        .putFields("name", Value.newBuilder().setStringValue("Alice").build()))
                .build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        metadataElement.writeDelimitedTo(output);
        namedQueryElement.writeDelimitedTo(output);
        documentMetadataElement.writeDelimitedTo(output);
        documentElement.writeDelimitedTo(output);

        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        assertThat(BundleElement.parseDelimitedFrom(input)).isEqualTo(metadataElement);
        assertThat(BundleElement.parseDelimitedFrom(input)).isEqualTo(namedQueryElement);
        assertThat(BundleElement.parseDelimitedFrom(input)).isEqualTo(documentMetadataElement);
        assertThat(BundleElement.parseDelimitedFrom(input)).isEqualTo(documentElement);
        assertThat(BundleElement.parseDelimitedFrom(input)).isNull();
    }

    @Test
    void descriptorsExposeBundleSchema() {
        Descriptors.FileDescriptor descriptor = BundleProto.getDescriptor();

        assertThat(descriptor.getPackage()).isEqualTo("google.firestore.bundle");
        assertThat(descriptor.getMessageTypes().stream().map(Descriptors.Descriptor::getName).toList())
                .containsExactly(
                        "BundledQuery",
                        "NamedQuery",
                        "BundledDocumentMetadata",
                        "BundleMetadata",
                        "BundleElement");

        Descriptors.Descriptor bundleElementDescriptor = BundleElement.getDescriptor();
        assertThat(bundleElementDescriptor.findFieldByName("metadata").getMessageType().getFullName())
                .isEqualTo("google.firestore.bundle.BundleMetadata");
        assertThat(bundleElementDescriptor.findFieldByName("document").getMessageType().getFullName())
                .isEqualTo("google.firestore.v1.Document");
        assertThat(bundleElementDescriptor.getOneofs().get(0).getName()).isEqualTo("element_type");

        Descriptors.EnumDescriptor limitTypeDescriptor = BundledQuery.LimitType.getDescriptor();
        assertThat(limitTypeDescriptor.findValueByName("FIRST").getNumber()).isZero();
        assertThat(limitTypeDescriptor.findValueByName("LAST").getNumber()).isOne();
        assertThat(BundledQuery.LimitType.valueOf(limitTypeDescriptor.findValueByNumber(1)))
                .isEqualTo(BundledQuery.LimitType.LAST);
    }

    @Test
    void buildersMergePartialMessagesAndPreserveUnknownFields() throws Exception {
        UnknownFieldSet unknownFields = UnknownFieldSet.newBuilder()
                .addField(99, UnknownFieldSet.Field.newBuilder().addVarint(12345L).build())
                .build();
        BundleMetadata baseMetadata = BundleMetadata.newBuilder()
                .setId("base")
                .setVersion(1)
                .setUnknownFields(unknownFields)
                .build();
        BundleMetadata overridingMetadata = BundleMetadata.newBuilder()
                .setId("override")
                .setTotalDocuments(3)
                .setTotalBytes(300L)
                .build();

        BundleMetadata mergedMetadata = baseMetadata.toBuilder()
                .mergeFrom(overridingMetadata)
                .buildPartial();

        assertThat(mergedMetadata.getId()).isEqualTo("override");
        assertThat(mergedMetadata.getVersion()).isOne();
        assertThat(mergedMetadata.getTotalDocuments()).isEqualTo(3);
        assertThat(mergedMetadata.getTotalBytes()).isEqualTo(300L);
        assertThat(mergedMetadata.getUnknownFields().getField(99).getVarintList()).containsExactly(12345L);

        BundleElement element = BundleElement.newBuilder().setMetadata(mergedMetadata).build();
        BundleElement parsedElement = BundleElement.parser().parseFrom(element.toByteString());
        assertThat(parsedElement.getMetadata().getUnknownFields().getField(99).getVarintList()).containsExactly(12345L);
        assertThat(parsedElement.getSerializedSize()).isEqualTo(element.getSerializedSize());
    }

    private static Timestamp timestamp(long seconds, int nanos) {
        return Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
    }
}
