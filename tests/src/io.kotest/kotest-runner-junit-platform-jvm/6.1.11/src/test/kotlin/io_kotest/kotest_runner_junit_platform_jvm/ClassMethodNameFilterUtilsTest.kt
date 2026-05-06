/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kotest.kotest_runner_junit_platform_jvm

import io.kotest.runner.junit.platform.KotestEngineDescriptor
import io.kotest.runner.junit.platform.KotestJunitPlatformTestEngine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.platform.engine.FilterResult
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.PostDiscoveryFilter
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import java.util.regex.Pattern

public class ClassMethodNameFilterUtilsTest {

    @Test
    fun `extracts and resets direct and delegated Gradle class method filters`(): Unit {
        val directCommandLinePatterns: MutableList<PatternValue> = mutableListOf(
            PatternValue("io_kotest.kotest_runner_junit_platform_jvm.FilteredSpec.outer -- inner"),
        )
        val directMatcher: TestSelectionMatcher = TestSelectionMatcher(
            commandLineIncludePatterns = directCommandLinePatterns,
            buildScriptIncludePatterns = mutableListOf(
                PatternValue("io_kotest.kotest_runner_junit_platform_jvm.FilteredSpec.*"),
            ),
        )
        val directFilter: ClassMethodNameFilter = ClassMethodNameFilter(directMatcher)

        val delegatedCommandLinePatterns: MutableList<PatternValue> = mutableListOf(
            PatternValue("io_kotest.kotest_runner_junit_platform_jvm.FilteredSpec.outer -- delegated"),
        )
        val delegatedPatternHolder: ClassTestSelectionMatcher = ClassTestSelectionMatcher(
            commandLineIncludePatterns = delegatedCommandLinePatterns,
            buildScriptIncludePatterns = mutableListOf(
                PatternValue("io_kotest.kotest_runner_junit_platform_jvm.FilteredSpec.delegated.*"),
            ),
        )
        val delegatedFilter: ClassMethodNameFilter = ClassMethodNameFilter(
            NewTestSelectionMatcher(classTestSelectionMatcher = delegatedPatternHolder),
        )
        val delegatingFilter: DelegatingByTypeFilter = DelegatingByTypeFilter(
            delegates = linkedMapOf(
                "class" to delegatedFilter,
                "method" to delegatedFilter,
            ),
        )

        val request: LauncherDiscoveryRequest = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(ClassMethodNameFilterUtilsTest::class.java))
            .filters(directFilter, delegatingFilter)
            .build()

        val descriptor: KotestEngineDescriptor = KotestJunitPlatformTestEngine().discover(
            request,
            UniqueId.forEngine("kotest"),
        )

        assertThat(descriptor.specs).isEmpty()
        assertThat(directCommandLinePatterns).isEmpty()
        assertThat(delegatedCommandLinePatterns).isEmpty()
    }
}

private class ClassMethodNameFilter(
    private val matcher: Any,
) : PostDiscoveryFilter {
    override fun apply(testDescriptor: TestDescriptor): FilterResult = FilterResult.included("test filter accepts all")
}

private class DelegatingByTypeFilter(
    private val delegates: Map<String, PostDiscoveryFilter>,
) : PostDiscoveryFilter {
    override fun apply(testDescriptor: TestDescriptor): FilterResult = FilterResult.included("delegating filter accepts all")
}

private class TestSelectionMatcher(
    private val commandLineIncludePatterns: MutableList<PatternValue>,
    private val buildScriptIncludePatterns: MutableList<PatternValue>,
)

private class NewTestSelectionMatcher(
    private val classTestSelectionMatcher: ClassTestSelectionMatcher,
)

private class ClassTestSelectionMatcher(
    private val commandLineIncludePatterns: MutableList<PatternValue>,
    private val buildScriptIncludePatterns: MutableList<PatternValue>,
)

private class PatternValue(pattern: String) {
    private val pattern: Pattern = Pattern.compile(pattern)
}
