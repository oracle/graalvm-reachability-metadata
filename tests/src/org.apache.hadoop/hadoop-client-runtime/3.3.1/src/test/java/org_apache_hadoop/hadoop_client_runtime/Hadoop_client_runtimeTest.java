/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_client_runtime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.apache.hadoop.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.shaded.org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.shaded.org.apache.commons.codec.binary.Hex;
import org.apache.hadoop.shaded.org.apache.commons.codec.digest.DigestUtils;
import org.apache.hadoop.thirdparty.com.google.common.base.CaseFormat;
import org.apache.hadoop.thirdparty.com.google.common.base.CharMatcher;
import org.apache.hadoop.thirdparty.com.google.common.base.Joiner;
import org.apache.hadoop.thirdparty.com.google.common.base.Splitter;
import org.apache.hadoop.thirdparty.com.google.common.cache.CacheBuilder;
import org.apache.hadoop.thirdparty.com.google.common.cache.CacheLoader;
import org.apache.hadoop.thirdparty.com.google.common.cache.LoadingCache;
import org.apache.hadoop.thirdparty.com.google.common.collect.ImmutableList;
import org.apache.hadoop.thirdparty.com.google.common.collect.ImmutableMap;
import org.apache.hadoop.thirdparty.com.google.common.collect.ImmutableSet;
import org.apache.hadoop.thirdparty.com.google.common.collect.Iterables;
import org.apache.hadoop.thirdparty.com.google.common.collect.Range;
import org.apache.hadoop.thirdparty.com.google.common.collect.RangeSet;
import org.apache.hadoop.thirdparty.com.google.common.collect.TreeRangeSet;
import org.apache.hadoop.thirdparty.com.google.common.hash.HashCode;
import org.apache.hadoop.thirdparty.com.google.common.hash.Hashing;
import org.apache.hadoop.thirdparty.com.google.common.io.ByteSource;
import org.apache.hadoop.thirdparty.com.google.common.io.ByteStreams;
import org.apache.hadoop.thirdparty.com.google.common.net.InternetDomainName;
import org.apache.hadoop.thirdparty.com.google.common.primitives.Ints;
import org.apache.hadoop.thirdparty.protobuf.Any;
import org.apache.hadoop.thirdparty.protobuf.ByteString;
import org.apache.hadoop.thirdparty.protobuf.DescriptorProtos;
import org.apache.hadoop.thirdparty.protobuf.Descriptors;
import org.apache.hadoop.thirdparty.protobuf.DynamicMessage;
import org.apache.hadoop.thirdparty.protobuf.InvalidProtocolBufferException;
import org.apache.hadoop.thirdparty.protobuf.StringValue;
import org.junit.jupiter.api.Test;

public class Hadoop_client_runtimeTest {
    @Test
    void shadedGuavaBaseAndCollectionsProvideDeterministicTransformations() {
        List<String> normalized = Splitter.on(',')
                .trimResults(CharMatcher.whitespace())
                .omitEmptyStrings()
                .splitToList(" alpha, beta,, gamma ");

        ImmutableMap<String, Integer> lengths = normalized.stream()
                .collect(ImmutableMap.toImmutableMap(
                        value -> CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, value + "_item"),
                        String::length));

        assertThat(normalized).containsExactly("alpha", "beta", "gamma");
        assertThat(Joiner.on('|').join(lengths.keySet())).isEqualTo("alphaItem|betaItem|gammaItem");
        assertThat(lengths).containsEntry("alphaItem", 5).containsEntry("betaItem", 4);
        assertThat(ImmutableSet.copyOf(lengths.values())).containsExactlyInAnyOrder(4, 5);
        assertThat(Iterables.getLast(normalized)).isEqualTo("gamma");
    }

    @Test
    void shadedGuavaRangesCachesHashesAndPrimitiveUtilitiesWorkTogether() throws Exception {
        RangeSet<Integer> allowedPorts = TreeRangeSet.create();
        allowedPorts.add(Range.closed(10, 20));
        allowedPorts.remove(Range.open(13, 17));

        LoadingCache<String, Integer> parsedIntegers = CacheBuilder.newBuilder()
                .maximumSize(4)
                .build(CacheLoader.from(value -> {
                    Integer parsed = Ints.tryParse(value);
                    return parsed == null ? -1 : parsed;
                }));

        byte[] payload = "hadoop-client-runtime".getBytes(UTF_8);
        HashCode hash = Hashing.sha256().hashBytes(payload);
        ByteSource source = ByteSource.wrap(payload);

        assertThat(allowedPorts.contains(12)).isTrue();
        assertThat(allowedPorts.contains(15)).isFalse();
        assertThat(allowedPorts.asRanges()).containsExactly(Range.closed(10, 13), Range.closed(17, 20));
        assertThat(parsedIntegers.get("17")).isEqualTo(17);
        assertThat(parsedIntegers.getUnchecked("not-a-number")).isEqualTo(-1);
        assertThat(hash.toString()).hasSize(64);
        assertThat(source.hash(Hashing.sha256())).isEqualTo(hash);
        assertThat(source.asCharSource(UTF_8).read()).isEqualTo("hadoop-client-runtime");
    }

    @Test
    void shadedGuavaDomainNamesUseBundledPublicSuffixData() {
        InternetDomainName name = InternetDomainName.from("NameNode.EXAMPLE.co.uk");

        assertThat(name.parts()).containsExactly("namenode", "example", "co", "uk");
        assertThat(name.hasPublicSuffix()).isTrue();
        assertThat(name.publicSuffix().toString()).isEqualTo("co.uk");
        assertThat(name.topPrivateDomain().toString()).isEqualTo("example.co.uk");
        assertThat(name.parent().toString()).isEqualTo("example.co.uk");
        assertThat(name.child("worker").toString()).isEqualTo("worker.namenode.example.co.uk");
        assertThat(InternetDomainName.isValid("invalid..domain")).isFalse();
    }

    @Test
    void shadedProtobufGeneratedMessagesRoundTripThroughByteArraysAndStreams() throws IOException {
        DescriptorProtos.FileDescriptorSet descriptorSet = DescriptorProtos.FileDescriptorSet.newBuilder()
                .addFile(DescriptorProtos.FileDescriptorProto.newBuilder()
                        .setName("cluster.proto")
                        .setPackage("example.hadoop")
                        .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                                .setName("Cluster")
                                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                                        .setName("name")
                                        .setNumber(1)
                                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL))))
                .build();

        byte[] encoded = descriptorSet.toByteArray();
        ByteString byteString = ByteString.copyFrom(encoded);
        DescriptorProtos.FileDescriptorSet parsed = DescriptorProtos.FileDescriptorSet.parseFrom(
                byteString.newInput());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        descriptorSet.writeDelimitedTo(output);
        DescriptorProtos.FileDescriptorSet delimited = DescriptorProtos.FileDescriptorSet.parseDelimitedFrom(
                new ByteArrayInputStream(output.toByteArray()));

        assertThat(parsed).isEqualTo(descriptorSet);
        assertThat(delimited.getFile(0).getMessageType(0).getField(0).getName()).isEqualTo("name");
        assertThat(byteString.substring(0, Math.min(4, byteString.size())).size()).isGreaterThan(0);
    }

    @Test
    void shadedProtobufAnyAndDynamicMessagesExerciseDescriptorBasedApis() throws Exception {
        StringValue value = StringValue.of("resource-manager");
        Any packed = Any.pack(value);

        DescriptorProtos.DescriptorProto queueMessage = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Queue")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("name")
                        .setNumber(1)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("capacity")
                        .setNumber(2)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL))
                .build();
        Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.buildFrom(
                DescriptorProtos.FileDescriptorProto.newBuilder()
                        .setSyntax("proto2")
                        .setName("queue.proto")
                        .setPackage("example.hadoop")
                        .addMessageType(queueMessage)
                        .build(),
                new Descriptors.FileDescriptor[0]);
        Descriptors.Descriptor descriptor = fileDescriptor.findMessageTypeByName("Queue");
        DynamicMessage message = DynamicMessage.newBuilder(descriptor)
                .setField(descriptor.findFieldByName("name"), "root.default")
                .setField(descriptor.findFieldByName("capacity"), 75)
                .build();
        DynamicMessage reparsed = DynamicMessage.parseFrom(descriptor, message.toByteArray());

        assertThat(packed.is(StringValue.class)).isTrue();
        assertThat(unpack(packed)).isEqualTo(value);
        assertThat(reparsed.getField(descriptor.findFieldByName("name"))).isEqualTo("root.default");
        assertThat(reparsed.getField(descriptor.findFieldByName("capacity"))).isEqualTo(75);
    }

    @Test
    void shadedJacksonTreeModelParsesMutatesAndWritesJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree("""
                {
                  "cluster": "test-cluster",
                  "nodes": ["nn", "dn"],
                  "active": true
                }
                """);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cluster", root.path("cluster").asText());
        response.put("nodes", Arrays.asList("nn", "dn", "rm"));
        response.put("healthy", root.path("active").asBoolean());

        String json = mapper.writeValueAsString(response);
        JsonNode reparsed = mapper.readTree(json);

        assertThat(root.path("nodes").get(1).asText()).isEqualTo("dn");
        assertThat(reparsed.path("nodes").size()).isEqualTo(3);
        assertThat(reparsed.path("healthy").asBoolean()).isTrue();
        assertThat(reparsed.path("cluster").asText()).isEqualTo("test-cluster");
    }

    @Test
    void shadedCommonsCodecAndGuavaIoHandleBinaryData() throws Exception {
        byte[] payload = "container-launch-context".getBytes(UTF_8);
        String base64 = Base64.encodeBase64String(payload);
        byte[] decoded = Base64.decodeBase64(base64);
        String hexDigest = Hex.encodeHexString(DigestUtils.sha256(payload));

        ByteArrayOutputStream copied = new ByteArrayOutputStream();
        long byteCount = ByteStreams.copy(new ByteArrayInputStream(decoded), copied);

        assertThat(base64).isEqualTo("Y29udGFpbmVyLWxhdW5jaC1jb250ZXh0");
        assertThat(copied.toByteArray()).isEqualTo(payload);
        assertThat(byteCount).isEqualTo(payload.length);
        assertThat(hexDigest).hasSize(64);
        assertThat(ImmutableList.copyOf(Ints.asList(1, 1, 2, 3, 5))).containsExactly(1, 1, 2, 3, 5);
    }

    private static StringValue unpack(Any packed) throws InvalidProtocolBufferException {
        return packed.unpack(StringValue.class);
    }
}
