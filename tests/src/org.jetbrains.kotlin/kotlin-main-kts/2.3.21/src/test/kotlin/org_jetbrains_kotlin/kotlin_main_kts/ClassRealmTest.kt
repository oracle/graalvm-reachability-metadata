/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_main_kts

import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.jetbrains.kotlin.org.codehaus.plexus.classworlds.ClassWorld
import org.jetbrains.kotlin.org.codehaus.plexus.classworlds.realm.ClassRealm
import org.junit.jupiter.api.Test
import java.util.Collections

public class ClassRealmTest {
    @Test
    fun selfLookupsUseUrlClassLoaderMethods(): Unit {
        val realm: TestClassRealm = TestClassRealm("self")
        val missingClassName: String = "example.DoesNotExist"
        val missingResourceName: String = "example/missing-resource.txt"

        assertThat(loadClassFromSelfAllowingNativeImageUnsupportedFeature(realm, missingClassName)).isNull()
        assertThat(realm.getResource(missingResourceName)).isNull()
        assertThat(Collections.list(realm.getResources(missingResourceName))).isEmpty()
    }

    @Test
    fun importLookupsDelegateToImportClassLoader(): Unit {
        val importClassLoader: BootstrapOnlyClassLoader = BootstrapOnlyClassLoader()
        val realm: TestClassRealm = TestClassRealm("import", importClassLoader = importClassLoader)
        val missingResourceName: String = "example/missing-import-resource.txt"

        assertThat(realm.loadClassFromImport(String::class.java.name)).isSameAs(String::class.java)
        assertThat(realm.loadResourceFromImport(missingResourceName)).isNull()
        assertThat(Collections.list(realm.loadResourcesFromImport(missingResourceName))).isEmpty()
    }

    @Test
    fun parentLookupsDelegateToParentClassLoader(): Unit {
        val parentClassLoader: BootstrapOnlyClassLoader = BootstrapOnlyClassLoader()
        val realm: TestClassRealm = TestClassRealm("parent", parentClassLoaderOverride = parentClassLoader)
        val missingResourceName: String = "example/missing-parent-resource.txt"

        assertThat(realm.loadClassFromParent(String::class.java.name)).isSameAs(String::class.java)
        assertThat(realm.loadResourceFromParent(missingResourceName)).isNull()
        assertThat(Collections.list(realm.loadResourcesFromParent(missingResourceName))).isEmpty()
    }

    private fun loadClassFromSelfAllowingNativeImageUnsupportedFeature(realm: ClassRealm, className: String): Class<*>? {
        return try {
            realm.loadClassFromSelf(className)
        } catch (error: Error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
            null
        }
    }

    public class TestClassRealm(
        id: String,
        private val importClassLoader: ClassLoader? = null,
        private val parentClassLoaderOverride: ClassLoader? = null,
    ) : ClassRealm(ClassWorld(), id, null) {
        override fun getImportClassLoader(name: String): ClassLoader? = importClassLoader

        override fun getParentClassLoader(): ClassLoader? = parentClassLoaderOverride
    }

    public class BootstrapOnlyClassLoader : ClassLoader(null)
}
