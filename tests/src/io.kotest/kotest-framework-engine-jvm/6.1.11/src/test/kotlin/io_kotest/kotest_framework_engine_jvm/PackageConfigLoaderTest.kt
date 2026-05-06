/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kotest.kotest_framework_engine_jvm

import io.kotest.core.config.AbstractPackageConfig
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.RootTest
import io.kotest.core.spec.Spec
import io.kotest.engine.config.SpecConfigResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class PackageConfig : AbstractPackageConfig() {
    override val isolationMode: IsolationMode = IsolationMode.InstancePerRoot
}

public class PackageConfiguredSpec : Spec() {
    override fun rootTests(): List<RootTest> = emptyList()
}

public class PackageConfigLoaderTest {
    @Test
    fun loadsPackageConfigFromSpecPackage(): Unit {
        val isolationMode: IsolationMode = SpecConfigResolver().isolationMode(PackageConfiguredSpec())

        assertThat(isolationMode).isEqualTo(IsolationMode.InstancePerRoot)
    }
}
