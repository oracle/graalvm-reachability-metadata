/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_groovydoc;

import org.codehaus.groovy.groovydoc.GroovyClassDoc;
import org.codehaus.groovy.groovydoc.GroovyType;
import org.codehaus.groovy.tools.groovydoc.ExternalGroovyClassDoc;
import org.codehaus.groovy.tools.groovydoc.SimpleGroovyClassDoc;
import org.codehaus.groovy.tools.groovydoc.SimpleGroovyFieldDoc;
import org.codehaus.groovy.tools.groovydoc.SimpleGroovyRootDoc;
import org.codehaus.groovy.tools.groovydoc.SimpleGroovyType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleGroovyClassDocTest {
    @Test
    void resolvesExplicitlyImportedFieldType() {
        GroovyType type = resolveFieldType(List.of("java/util/ArrayList"), "ArrayList");

        assertThat(type).isInstanceOf(ExternalGroovyClassDoc.class);
        assertThat(type.qualifiedTypeName()).isEqualTo("java.util.ArrayList");
    }

    @Test
    void resolvesFieldTypeFromWildcardImport() {
        GroovyType type = resolveFieldType(List.of("java/util/*"), "List");

        assertThat(type).isInstanceOf(ExternalGroovyClassDoc.class);
        assertThat(type.qualifiedTypeName()).isEqualTo("java.util.List");
    }

    @Test
    void resolvesFullyQualifiedFieldType() {
        GroovyType type = resolveFieldType(List.of(), "java/time/LocalDate");

        assertThat(type).isInstanceOf(ExternalGroovyClassDoc.class);
        assertThat(type.qualifiedTypeName()).isEqualTo("java.time.LocalDate");
    }

    @Test
    void resolvesImplicitJavaLangFieldType() {
        GroovyType type = resolveFieldType(List.of(), "String");

        assertThat(type).isInstanceOf(ExternalGroovyClassDoc.class);
        assertThat(type.qualifiedTypeName()).isEqualTo("java.lang.String");
    }

    @Test
    void resolvesNestedExternalFieldType() {
        GroovyType type = resolveFieldType(List.of(), "java/util/Map/Entry");

        assertThat(type).isInstanceOf(ExternalGroovyClassDoc.class);
        assertThat(type.qualifiedTypeName()).isEqualTo("java.util.Map$Entry");
        assertThat(((ExternalGroovyClassDoc) type).getTypeSourceDescription()).isEqualTo("interface");
    }

    @Test
    void discoversParentClassesForDocumentedCollection() {
        SimpleGroovyClassDoc classDoc = new SimpleGroovyClassDoc(List.of(), "ArrayList");
        classDoc.setFullPathName("java/util/ArrayList");

        List<GroovyClassDoc> parents = classDoc.getParentClasses();

        assertThat(parents).extracting(GroovyClassDoc::qualifiedTypeName).containsExactly(
                "java.lang.Object",
                "java.util.AbstractCollection",
                "java.util.AbstractList",
                "java.util.ArrayList");
        assertThat(classDoc.superclass().qualifiedTypeName()).isEqualTo("java.util.AbstractList");
    }

    private static GroovyType resolveFieldType(List<String> imports, String typeName) {
        SimpleGroovyClassDoc classDoc = new SimpleGroovyClassDoc(imports, "Catalog");
        classDoc.setFullPathName("example/Catalog");
        SimpleGroovyFieldDoc field = new SimpleGroovyFieldDoc("items", classDoc);
        field.setType(new SimpleGroovyType(typeName));
        classDoc.add(field);
        SimpleGroovyRootDoc rootDoc = new SimpleGroovyRootDoc("Catalog documentation");
        rootDoc.putAllClasses(Map.of(classDoc.getFullPathName(), classDoc));

        rootDoc.resolve();

        assertThat(classDoc.fields()).containsExactly(field);
        return field.type();
    }
}
