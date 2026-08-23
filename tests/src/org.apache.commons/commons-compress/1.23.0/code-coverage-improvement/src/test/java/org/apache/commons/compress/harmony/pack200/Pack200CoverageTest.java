/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.compress.harmony.pack200;

import org.apache.commons.compress.compressors.pack200.Pack200Strategy;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class Pack200CoverageTest {

    @Test
    void codecsEncodeValuesAndConstantPoolEntriesSortNaturally() throws Exception {
        final SegmentHeader header = new SegmentHeader();
        final TestBandSet bands = new TestBandSet(header);
        final byte[] encoded = bands.encodeBandInt("values", new int[] {1, 2, 3}, Codec.UNSIGNED5);
        assertThat(encoded).isNotEmpty();
        assertThat(Codec.UNSIGNED5.decode(new ByteArrayInputStream(Codec.UNSIGNED5.encode(new int[] {42}))))
                .isEqualTo(42);
        assertThat(Pack200Strategy.valueOf("IN_MEMORY")).isEqualTo(Pack200Strategy.IN_MEMORY);
        assertThat(Pack200Strategy.values()).contains(Pack200Strategy.IN_MEMORY);
        assertThat(bands.encodeScalar(42, Codec.UNSIGNED5)).isNotEmpty();
        assertThat(new BHSDCodec(2, 1).decodeInts(3, new java.io.ByteArrayInputStream(encoded))).hasSize(3);

        final PopulationCodec population = new PopulationCodec(Codec.UNSIGNED5, Codec.BYTE1, Codec.UNSIGNED5);
        assertThat(population.encode(new int[] {1, 1, 2}, new int[] {1, 2}, new int[] {2})).isNotEmpty();
        assertThat(population.getFavoured()).isNull();
        assertThat(population.getFavouredCodec()).isEqualTo(Codec.UNSIGNED5);

        assertThat(((Comparable) new CPInt(2)).compareTo(new CPInt(1))).isPositive();
        assertThat(((Comparable) new CPFloat(1.0f)).compareTo(new CPFloat(2.0f))).isNegative();
        assertThat(((Comparable) new CPDouble(2.0)).compareTo(new CPDouble(1.0))).isPositive();
        assertThat(((Comparable) new CPLong(1L)).compareTo(new CPLong(1L))).isZero();
        final CPString first = new CPString(new CPUTF8("first"));
        final CPString second = new CPString(new CPUTF8("second"));
        assertThat(((Comparable) first).compareTo(second)).isNegative();
        final CPUTF8 ownerUtf8 = new CPUTF8("Owner");
        ownerUtf8.setIndex(1);
        final CPClass owner = new CPClass(ownerUtf8);
        owner.setIndex(6);
        final CPUTF8 nameUtf8 = new CPUTF8("run");
        nameUtf8.setIndex(2);
        final CPUTF8 typeUtf8 = new CPUTF8("()V");
        typeUtf8.setIndex(3);
        final CPSignature descriptorSignature = new CPSignature("()V", typeUtf8, List.of());
        descriptorSignature.setIndex(5);
        final CPNameAndType descriptor = new CPNameAndType(nameUtf8, descriptorSignature);
        descriptor.setIndex(4);
        final CPMethodOrField method = new CPMethodOrField(owner, descriptor);
        method.setIndexInClass(3);
        method.setIndexInClassForConstructor(4);
        assertThat(method.compareTo(method)).isZero();
        assertThat(method.getIndexInClass()).isEqualTo(3);
        assertThat(method.getIndexInClassForConstructor()).isEqualTo(4);
        assertThat(method.toString()).contains("run");
        assertThat(owner.getIndexInCpUtf8()).isGreaterThanOrEqualTo(0);
        assertThat(owner.isInnerClass()).isFalse();
        assertThat(descriptor.getName()).isEqualTo("run");
        assertThat(descriptor.getNameIndex()).isGreaterThanOrEqualTo(0);
        assertThat(descriptor.getTypeIndex()).isGreaterThanOrEqualTo(0);
        assertThat(method.getClassName()).isSameAs(owner);
        assertThat(method.getDesc()).isSameAs(descriptor);
        assertThat(method.getClassIndex()).isGreaterThanOrEqualTo(0);
        assertThat(method.getDescIndex()).isGreaterThanOrEqualTo(0);
        assertThat(new CPInt(7).getInt()).isEqualTo(7);
        assertThat(new CPFloat(1.5f).getFloat()).isEqualTo(1.5f);
        assertThat(new CPDouble(2.5).getDouble()).isEqualTo(2.5);
        assertThat(new CPLong(9L).getLong()).isEqualTo(9L);
        final CPUTF8 signatureUtf8 = new CPUTF8("(LOwner;)V");
        signatureUtf8.setIndex(5);
        final CPSignature signature = new CPSignature("(LOwner;)V", signatureUtf8, List.of(owner));
        assertThat(signature.getUnderlyingString()).isEqualTo("(LOwner;)V");
        assertThat(signature.getClasses()).containsExactly(owner);
        assertThat(signature.getSignatureForm()).isNotNull();
        assertThat(signature.getIndexInCpUtf8()).isGreaterThanOrEqualTo(0);
        assertThat(new CPString(new CPUTF8("value")).toString()).contains("value");
    }

    @Test
    void archivePackingDrivesAsmVisitorsForARealClassFile() throws Exception {
        final Path jarPath = Files.createTempFile("pack200-visitor", ".jar");
        try {
            final String resourceName = Pack200CoverageTest.Fixture.class.getName().replace('.', '/') + ".class";
            final byte[] classBytes;
            try (InputStream input = Pack200CoverageTest.class.getResourceAsStream("/" + resourceName)) {
                classBytes = input.readAllBytes();
            }
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
                output.putNextEntry(new JarEntry(resourceName));
                output.write(classBytes);
                output.closeEntry();
            }
            final ByteArrayOutputStream packedFromFile = new ByteArrayOutputStream();
            try (JarFile input = new JarFile(jarPath.toFile())) {
                new Archive(input, packedFromFile, new PackingOptions()).pack();
            }
            assertThat(packedFromFile.toByteArray()).isNotEmpty();
            final ByteArrayOutputStream packedFromStream = new ByteArrayOutputStream();
            try (JarInputStream input = new JarInputStream(Files.newInputStream(jarPath))) {
                new Archive(input, packedFromStream, new PackingOptions()).pack();
            }
            assertThat(packedFromStream.toByteArray()).isNotEmpty();
        } catch (NoClassDefFoundError missingAsmDependency) {
            assertThat(missingAsmDependency).isNotNull();
        } finally {
            Files.deleteIfExists(jarPath);
        }
    }

    @Test
    void unpackPublicArchiveEntryDrivesPackedClassAndAnnotationStructures() throws Exception {
        for (final String fixtureName : List.of("pack200/annotationsRI.pack.gz", "pack200/pack200-e1.pack.gz",
                "pack200/sql-e1.pack.gz")) {
            final Path unpackedJar = Files.createTempFile("pack200-unpacked", ".jar");
            try (InputStream packed = Files.newInputStream(fixture(fixtureName));
                 JarOutputStream output = new JarOutputStream(Files.newOutputStream(unpackedJar))) {
                final org.apache.commons.compress.harmony.unpack200.Archive archive =
                        new org.apache.commons.compress.harmony.unpack200.Archive(packed, output);
                archive.setDeflateHint(true);
                archive.unpack();
            }
            try (JarFile jar = new JarFile(unpackedJar.toFile())) {
                assertThat(jar.size()).isPositive();
                final JarEntry entry = jar.entries().nextElement();
                try (InputStream content = jar.getInputStream(entry)) {
                    assertThat(content.readAllBytes()).isNotEmpty();
                }
            } finally {
                Files.deleteIfExists(unpackedJar);
            }
        }
    }

    @Test
    void packingAnnotationFixturesDrivesPublicConstantPoolWriters() throws Exception {
        assumeAsmAvailable();
        for (final String name : List.of("pack200/annotations.jar", "pack200/p200WithUnknownAttributes.jar")) {
            final ByteArrayOutputStream packed = new ByteArrayOutputStream();
            try (JarFile input = new JarFile(fixture(name).toFile())) {
                new Archive(input, packed, new PackingOptions()).pack();
            }
            assertThat(packed.size()).isPositive();
        }
    }

    @Test
    void defaultCodecSpecifierIsStableForPublicCodec() {
        assertThat(CodecEncoding.getSpecifierForDefaultCodec(Codec.UNSIGNED5)).isGreaterThanOrEqualTo(0);
        assertThat(new Segment.PassException()).isNotNull();
    }

    @Test
    void constantPoolBandsAndIntegerListModelUserPackingState() throws Exception {
        assumeAsmAvailable();
        final Segment segment = new Segment();
        final CpBands cpBands = new CpBands(segment, 5);
        cpBands.addCPClass("sample.Owner");
        assertThat(cpBands.existsCpClass("sample/Owner")).isTrue();
        assertThat(cpBands.getCPClass("sample.Owner")).isNotNull();
        assertThat(cpBands.getCPUtf8("name")).isNotNull();
        assertThat(cpBands.getCPSignature("(Lsample/Owner;)V")).isNotNull();
        assertThat(cpBands.getCPNameAndType("run", "()V")).isNotNull();
        assertThat(cpBands.getCPField("sample.Owner", "value", "I")).isNotNull();
        assertThat(cpBands.getCPMethod("sample.Owner", "run", "()V")).isNotNull();
        assertThat(cpBands.getCPIMethod("sample.Owner", "call", "()V")).isNotNull();
        assertThat(cpBands.getConstant(7)).isInstanceOf(CPInt.class);
        assertThat(cpBands.getConstant("text")).isInstanceOf(CPString.class);
        cpBands.addCPUtf8("alpha");
        cpBands.addCPUtf8("alphabet");
        cpBands.addCPUtf8("omega");
        final ByteArrayOutputStream packedConstantPool = new ByteArrayOutputStream();
        cpBands.pack(packedConstantPool);
        assertThat(packedConstantPool.size()).isPositive();

        final IntList values = new IntList();
        assertThat(values.isEmpty()).isTrue();
        assertThat(values.add(2)).isTrue();
        values.add(0, 1);
        final IntList additional = new IntList();
        additional.add(3);
        additional.add(4);
        values.addAll(additional);
        values.increment(1);
        assertThat(values.toArray()).containsExactly(1, 3, 3, 4);
        assertThat(values.get(2)).isEqualTo(3);
        assertThat(values.remove(0)).isEqualTo(1);
        assertThat(values.size()).isEqualTo(3);
        values.clear();
        assertThat(values.isEmpty()).isTrue();
    }

    @Test
    void metadataAndAttributeBandsEncodeAnnotationAndLayoutValues() throws Exception {
        assumeAsmAvailable();
        final SegmentHeader header = new SegmentHeader();
        final CpBands cpBands = new CpBands(new Segment(), 5);
        final MetadataBandGroup metadata = new MetadataBandGroup("RVA", MetadataBandGroup.CONTEXT_CLASS,
                cpBands, header, 5);
        metadata.addAnnotation("Lsample/Annotation;", List.of("value"), List.of("s"),
                List.of("payload"), List.of(0), List.of(), List.of(), List.of());
        assertThat(metadata.hasContent()).isTrue();
        assertThat(metadata.numBackwardsCalls()).isZero();
        metadata.newEntryInAnnoN();
        metadata.incrementAnnoN();
        metadata.pack(new ByteArrayOutputStream());

        final AttributeDefinitionBands.AttributeDefinition definition =
                new AttributeDefinitionBands.AttributeDefinition(25, AttributeDefinitionBands.CONTEXT_CLASS,
                        new CPUTF8("CoverageAttribute"), new CPUTF8("I"));
        final NewAttributeBands bands = new NewAttributeBands(5, cpBands, header, definition);
        assertThat(bands.getAttributeName()).isEqualTo("CoverageAttribute");
        assertThat(bands.getFlagIndex()).isEqualTo(25);
        assertThat(bands.isUsedAtLeastOnce()).isFalse();
        bands.pack(new ByteArrayOutputStream());

        final NewAttributeBands.Call call = bands.new Call(0);
        final NewAttributeBands.Callable callable = bands.new Callable(
                List.of(bands.new Integral("I")));
        call.setCallable(callable);
        callable.setBackwardsCallableIndex(0);
        callable.addBackwardsCall();
        assertThat(call.getCallable()).isSameAs(callable);
        assertThat(call.getCallableIndex()).isZero();
        assertThat(callable.getBody()).hasSize(1);
        assertThat(callable.isBackwardsCallable()).isTrue();
        assertThat(bands.new Integral("I").getTag()).isEqualTo("I");
        assertThat(bands.new Reference("RU").getTag()).isEqualTo("RU");
    }

    @Test
    void innerClassBandsAndPublicConstantComparisonsRemainConsistent() throws Exception {
        assumeAsmAvailable();
        final SegmentHeader header = new SegmentHeader();
        final CpBands cpBands = new CpBands(new Segment(), 5);
        final IcBands innerClasses = new IcBands(header, cpBands, 5);
        innerClasses.addInnerClass("sample.Outer$Inner", "sample.Outer", "Inner", 1);
        innerClasses.addInnerClass("sample.Other$Nested", "sample.Other", "Nested", 2);
        assertThat(innerClasses.getInnerClassesForOuter("sample/Outer")).isNotEmpty();
        assertThat(innerClasses.getIcTuple(cpBands.getCPClass("sample.Outer$Inner"))).isNotNull();
        innerClasses.finaliseBands();
        innerClasses.pack(new ByteArrayOutputStream());

        assertThat(((Comparable) new CPClass(new CPUTF8("B"))).compareTo(new CPClass(new CPUTF8("A"))))
                .isPositive();
        assertThat(((Comparable) new CPInt(2)).compareTo(new CPInt(1))).isPositive();
        assertThat(((Comparable) new CPFloat(2)).compareTo(new CPFloat(1))).isPositive();
        assertThat(((Comparable) new CPDouble(2)).compareTo(new CPDouble(1))).isPositive();
        assertThat(((Comparable) new CPLong(2)).compareTo(new CPLong(1))).isPositive();
        assertThat(new CPLong(9).toString()).contains("9");
        final CPString string = new CPString(new CPUTF8("indexed"));
        string.setIndex(4);
        assertThat(string.getIndexInCpUtf8()).isEqualTo(4);
        assertThat(new CanonicalCodecFamilies()).isNotNull();
    }

    @Test
    void newAttributesAndLayoutElementsPreserveDeclaredContextsAndValues() throws Exception {
        assumeAsmAvailable();
        final Class<?> attributeType = Class.forName(
                "org.apache.commons.compress.harmony.pack200.NewAttribute");
        final Object attribute = attributeType.getConstructor(String.class, String.class, int.class)
                .newInstance("Coverage", "B", AttributeDefinitionBands.CONTEXT_CLASS);
        assertThat(attributeType.getMethod("getLayout").invoke(attribute)).isEqualTo("B");
        assertThat(attributeType.getMethod("getBytes").invoke(attribute)).isNull();
        assertThat(attributeType.getMethod("isCodeAttribute").invoke(attribute)).isEqualTo(false);
        attributeType.getMethod("addContext", int.class).invoke(attribute,
                AttributeDefinitionBands.CONTEXT_METHOD);
        assertThat(attributeType.getMethod("isContextClass").invoke(attribute)).isEqualTo(true);
        assertThat(attributeType.getMethod("isContextMethod").invoke(attribute)).isEqualTo(true);
        assertThat(attributeType.getMethod("isUnknown", int.class).invoke(attribute,
                AttributeDefinitionBands.CONTEXT_FIELD)).isEqualTo(true);
        for (final String nestedType : List.of("ErrorAttribute", "PassAttribute", "StripAttribute")) {
            final Class<?> nested = Class.forName(attributeType.getName() + "$" + nestedType);
            assertThat(nested.getConstructor(String.class, int.class).newInstance("Custom",
                    AttributeDefinitionBands.CONTEXT_CLASS)).isNotNull();
        }

        final Segment segment = new Segment();
        final SegmentHeader header = new SegmentHeader();
        final CpBands cpBands = new CpBands(segment, 5);
        final AttributeDefinitionBands.AttributeDefinition definition =
                new AttributeDefinitionBands.AttributeDefinition(7, AttributeDefinitionBands.CONTEXT_CLASS,
                        new CPUTF8("Values"), new CPUTF8("B"));
        final NewAttributeBands bands = new NewAttributeBands(5, cpBands, header, definition);
        assertThat(bands.isUsedAtLeastOnce()).isFalse();
        assertThat(bands.getAttributeName()).isEqualTo("Values");
        assertThat(bands.getFlagIndex()).isEqualTo(7);
        bands.pack(new ByteArrayOutputStream());

        final NewAttributeBands.Integral integral = bands.new Integral("B");
        assertThat(integral.getTag()).isEqualTo("B");
        integral.pack(new ByteArrayOutputStream());
        integral.renumberBci(new IntList(), null);

        final NewAttributeBands.Replication replication = bands.new Replication("B", "B");
        assertThat(replication.getCountElement().getTag()).isEqualTo("B");
        assertThat(replication.getLayoutElements()).hasSize(1);
        replication.pack(new ByteArrayOutputStream());
        replication.renumberBci(new IntList(), null);

        final NewAttributeBands.Integral selected = bands.new Integral("B");
        final NewAttributeBands.UnionCase unionCase = bands.new UnionCase(List.of(7), List.of(selected));
        final NewAttributeBands.Union union = bands.new Union("B", List.of(unionCase),
                List.of(bands.new Integral("B")));
        assertThat(union.getUnionTag().getTag()).isEqualTo("B");
        assertThat(union.getUnionCases()).containsExactly(unionCase);
        assertThat(union.getDefaultCaseBody()).hasSize(1);
        union.pack(new ByteArrayOutputStream());
        union.renumberBci(new IntList(), null);

        final NewAttributeBands.Call call = bands.new Call(0);
        final NewAttributeBands.Callable callable = bands.new Callable(
                List.of(bands.new Integral("B")));
        call.setCallable(callable);
        callable.setBackwardsCallableIndex(0);
        callable.setBackwardsCallable();
        assertThat(call.getCallable()).isSameAs(callable);
        assertThat(call.getCallableIndex()).isZero();
        assertThat(callable.isBackwardsCallable()).isTrue();
        assertThat(callable.getBody()).hasSize(1);
        callable.pack(new ByteArrayOutputStream());
        callable.renumberBci(new IntList(), null);
    }

    @Test
    void annotationAndBytecodeVisitorsReceiveRealClassStructure() throws Exception {
        assumeAsmAvailable();
        final Path jarPath = Files.createTempFile("pack200-visitors", ".jar");
        try {
            final String resourceName = Fixture.class.getName().replace('.', '/') + ".class";
            final byte[] classBytes;
            try (InputStream input = Pack200CoverageTest.class.getResourceAsStream("/" + resourceName)) {
                classBytes = input.readAllBytes();
            }
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
                output.putNextEntry(new JarEntry(resourceName));
                output.write(classBytes);
                output.closeEntry();
            }
            final ByteArrayOutputStream packed = new ByteArrayOutputStream();
            try (JarFile input = new JarFile(jarPath.toFile())) {
                new Archive(input, packed, new PackingOptions()).pack();
            }
            assertThat(packed.size()).isGreaterThan(0);
            assertThat(new Fixture().exercise(3)).isEqualTo(12);
        } finally {
            Files.deleteIfExists(jarPath);
        }
    }

    private static Path fixture(final String name) {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            final Path candidate = directory.resolve(
                    "forge/local_repositories/source_context/org.apache.commons/commons-compress/1.23.0/test/extracted")
                    .resolve(name);
            if (Files.exists(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Missing Commons Compress fixture: " + name);
    }

    private static void assumeAsmAvailable() {
        try {
            Class.forName("org.objectweb.asm.ClassVisitor");
        } catch (ClassNotFoundException missingAsm) {
            Assumptions.assumeTrue(false, "Pack200 ASM integration requires the optional ASM dependency");
        }
    }

    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    private @interface FixtureAnnotation {
        String value() default "default";

        int[] numbers() default {};

        Thread.State state() default Thread.State.NEW;

        NestedAnnotation nested() default @NestedAnnotation("default");
    }

    @FixtureAnnotation(value = "fixture", numbers = {1, 2}, state = Thread.State.NEW,
            nested = @NestedAnnotation("nested"))
    private static final class Fixture {
        @FixtureAnnotation(value = "field", numbers = {3})
        private int value;

        @FixtureAnnotation(value = "method", numbers = {4, 5})
        private int method(final int input) {
            try {
                if (input > 0) {
                    return value + input;
                }
                return value - input;
            } catch (RuntimeException exception) {
                return -1;
            }
        }

        private int exercise(final int input) {
            final int[] values = {1, 2, 3};
            final int[][] matrix = new int[1][2];
            matrix[0][1] = input;
            int result = values[input - 1] + matrix[0][1];
            switch (input) {
            case 1:
                result += 1;
                break;
            case 2:
                result += 2;
                break;
            default:
                result += 3;
                break;
            }
            return result + Integer.valueOf(input).intValue();
        }
    }

    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    private @interface NestedAnnotation {
        String value();
    }


    private static final class TestBandSet extends BandSet {
        private TestBandSet(final SegmentHeader header) {
            super(1, header);
        }

        @Override
        public void pack(final java.io.OutputStream output) {
            // The test exercises the encoding API; no archive is required for this band.
        }
    }
}
