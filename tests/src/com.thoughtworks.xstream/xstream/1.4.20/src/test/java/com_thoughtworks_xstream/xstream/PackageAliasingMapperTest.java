/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.core.ClassLoaderReference;
import com.thoughtworks.xstream.mapper.DefaultMapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;
import com.thoughtworks.xstream.mapper.PackageAliasingMapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PackageAliasingMapperTest {
    private static final String ALIAS_PACKAGE = "sample.alias";

    @Test
    void preservesPackageAliasesAcrossCustomSerializationRoundTrip() {
        PackageAliasingMapper mapper = new PackageAliasingMapper(new DefaultMapper(new ClassLoaderReference(
            PackageAliasingMapperTest.class.getClassLoader())));
        mapper.addPackageAlias(ALIAS_PACKAGE, PackageAliasingMapperTest.class.getPackage().getName());

        XStream xstream = packageAliasingMapperXStream();
        String xml = xstream.toXML(mapper);
        PackageAliasingMapper restored = (PackageAliasingMapper)xstream.fromXML(xml);

        assertThat(restored.serializedClass(PackageAliasingMapperTest.class))
            .isEqualTo(ALIAS_PACKAGE + ".PackageAliasingMapperTest");
    }

    private static XStream packageAliasingMapperXStream() {
        XStream xstream = new XStream();
        xstream.allowTypes(new Class[]{PackageAliasingMapper.class});
        omitMapperWrapperField(xstream, "wrapped");
        omitMapperWrapperField(xstream, "aliasForAttributeMapper");
        omitMapperWrapperField(xstream, "aliasForSystemAttributeMapper");
        omitMapperWrapperField(xstream, "attributeForAliasMapper");
        omitMapperWrapperField(xstream, "defaultImplementationOfMapper");
        omitMapperWrapperField(xstream, "getConverterFromAttributeMapper");
        omitMapperWrapperField(xstream, "getConverterFromItemTypeMapper");
        omitMapperWrapperField(xstream, "getFieldNameForItemTypeAndNameMapper");
        omitMapperWrapperField(xstream, "getImplicitCollectionDefForFieldNameMapper");
        omitMapperWrapperField(xstream, "getItemTypeForItemFieldNameMapper");
        omitMapperWrapperField(xstream, "getLocalConverterMapper");
        omitMapperWrapperField(xstream, "isIgnoredElementMapper");
        omitMapperWrapperField(xstream, "isImmutableValueTypeMapper");
        omitMapperWrapperField(xstream, "isReferenceableMapper");
        omitMapperWrapperField(xstream, "realClassMapper");
        omitMapperWrapperField(xstream, "realMemberMapper");
        omitMapperWrapperField(xstream, "serializedClassMapper");
        omitMapperWrapperField(xstream, "serializedMemberMapper");
        omitMapperWrapperField(xstream, "shouldSerializeMemberMapper");
        return xstream;
    }

    private static void omitMapperWrapperField(XStream xstream, String fieldName) {
        xstream.omitField(MapperWrapper.class, fieldName);
    }
}
