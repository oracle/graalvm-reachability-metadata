/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sksamuel_hoplite.hoplite_core

import com.sksamuel.hoplite.ClasspathResourceLoader
import com.sksamuel.hoplite.ClasspathResourceLoader.Companion.toClasspathResourceLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class ClasspathResourceLoaderInnerCompanionInnerToClasspathResourceLoaderAnonymous2Test {
    @Test
    fun classLoaderBasedLoaderReturnsNullForMissingResourceLookups(): Unit {
        val classLoader: ClassLoader = this.javaClass.classLoader
        val loader: ClasspathResourceLoader = classLoader.toClasspathResourceLoader()
        val missingResourceName: String = "definitely-missing-hoplite-classloader-resource.conf"

        assertThat(loader.getResource(missingResourceName)).isNull()
        assertThat(loader.getResourceAsStream(missingResourceName)).isNull()
    }
}
