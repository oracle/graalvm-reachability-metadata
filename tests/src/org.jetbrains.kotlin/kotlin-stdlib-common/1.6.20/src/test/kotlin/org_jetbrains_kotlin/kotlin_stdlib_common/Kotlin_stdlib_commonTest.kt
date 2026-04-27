/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib_common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.DeepRecursiveFunction
import kotlin.properties.Delegates
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

public class Kotlin_stdlib_commonTest {
    @Test
    fun resultApiTransformsAndRecoversAcrossBranches() {
        val events = mutableListOf<String>()

        val parsedVersion = runCatching { "1.6.20".split('.').map(String::toInt) }
            .onSuccess { parts -> events += "parsed:${parts.joinToString("-")}" }
            .mapCatching { parts -> parts[0] * 100 + parts[1] * 10 + parts[2] / 10 }
            .getOrThrow()

        val recoveredVersion = runCatching { "1.6.x".split('.').map(String::toInt) }
            .mapCatching { parts -> parts.sum() }
            .recover { error ->
                events += "recovered:${error.javaClass.simpleName}"
                -1
            }
            .getOrThrow()

        assertThat(parsedVersion).isEqualTo(162)
        assertThat(recoveredVersion).isEqualTo(-1)
        assertThat(events).containsExactly(
            "parsed:1-6-20",
            "recovered:NumberFormatException",
        )
    }

    @Test
    fun collectionBuildersAndWindowingRetainStableOrdering() {
        val versions = buildList {
            add("1.5.31")
            add("1.6.20")
            add("1.7.10")
            add("1.8.0")
        }
        val labelsByMinor = buildMap {
            versions.forEach { version ->
                put(version.substringBeforeLast('.'), "kotlin-stdlib-common:$version")
            }
        }
        val supportedMinors = buildSet {
            addAll(versions.map { version -> version.substringBeforeLast('.') })
        }
        val upgradePath = versions.zipWithNext { from, to -> "$from->$to" }
        val releaseWindows = versions.windowed(size = 2, step = 2, partialWindows = true)

        assertThat(labelsByMinor).containsEntry("1.6", "kotlin-stdlib-common:1.6.20")
        assertThat(supportedMinors).containsExactly("1.5", "1.6", "1.7", "1.8")
        assertThat(upgradePath).containsExactly(
            "1.5.31->1.6.20",
            "1.6.20->1.7.10",
            "1.7.10->1.8.0",
        )
        assertThat(releaseWindows).containsExactly(
            listOf("1.5.31", "1.6.20"),
            listOf("1.7.10", "1.8.0"),
        )
    }

    @Test
    fun delegatedPropertiesAndLazyValuesTrackStateChanges() {
        val moduleState = ModuleState()

        assertThat(moduleState.events).isEmpty()
        assertThat(moduleState.normalizedCoordinate).isEqualTo("org.jetbrains.kotlin:kotlin.stdlib.common")

        moduleState.releaseChannel = "rc"
        moduleState.releaseChannel = "stable"
        moduleState.priority = 4
        moduleState.priority = 2

        assertThat(moduleState.releaseChannel).isEqualTo("stable")
        assertThat(moduleState.priority).isEqualTo(4)
        assertThat(moduleState.events).containsExactly(
            "lazy:normalize",
            "channel:draft->rc",
            "channel:rc->stable",
            "priority:accepted:0->4",
            "priority:rejected:4->2",
        )
    }

    @Test
    fun sequencePipelinesStayLazyAndSupportScopedConsumption() {
        val trace = mutableListOf<String>()
        val generatedVersions = sequence {
            for (minor in 5..7) {
                trace += "yield:$minor"
                yield(minor)
            }
        }.map { minor ->
            trace += "map:$minor"
            "1.$minor.20"
        }

        assertThat(trace).isEmpty()

        val firstTwo = generatedVersions.take(2).toList()
        val minorLabels = sequenceOf("1.5.31", "1.6.20", "1.7.10")
            .map { version -> version.substringBeforeLast('.') }
            .distinct()
            .toList()

        assertThat(firstTwo).containsExactly("1.5.20", "1.6.20")
        assertThat(trace).containsExactly(
            "yield:5",
            "map:5",
            "yield:6",
            "map:6",
        )
        assertThat(minorLabels).containsExactly("1.5", "1.6", "1.7")
    }

    @Test
    fun arrayDequeSupportsBidirectionalConsumptionWithoutBreakingLogicalOrder() {
        val releaseStages = ArrayDeque(listOf("alpha", "beta"))

        releaseStages.addFirst("preview")
        releaseStages.addLast("stable")

        val promotedStages = listOf(
            releaseStages.removeFirst(),
            releaseStages.removeFirst(),
        )
        releaseStages.addFirst("candidate")
        val rollbackOrder = listOf(
            releaseStages.removeLast(),
            releaseStages.removeLast(),
            releaseStages.removeLast(),
        )

        assertThat(promotedStages).containsExactly("preview", "alpha")
        assertThat(rollbackOrder).containsExactly("stable", "beta", "candidate")
        assertThat(releaseStages.removeFirstOrNull()).isNull()
    }

    @Test
    fun durationArithmeticAndStringBuildersSummarizeTheTestMatrix() {
        val startupBudget = 750.milliseconds + 2.seconds
        val report = buildString {
            append("group=org.jetbrains.kotlin\n")
            append("artifact=kotlin-stdlib-common\n")
            append("version=1.6.20")
        }
        val normalizedReport = report.lineSequence()
            .map { line ->
                val key = line.substringBefore('=').uppercase()
                val value = line.substringAfter('=')
                "$key:$value"
            }
            .joinToString(separator = " | ")

        assertThat(startupBudget.inWholeMilliseconds).isEqualTo(2750)
        assertThat((startupBudget - 250.milliseconds).inWholeSeconds).isEqualTo(2)
        assertThat(normalizedReport).isEqualTo(
            "GROUP:org.jetbrains.kotlin | ARTIFACT:kotlin-stdlib-common | VERSION:1.6.20",
        )
    }

    @Test
    fun deepRecursiveFunctionTraversesDeepChainsWithoutConsumingTheCallStack() {
        val releaseChain = (1..2048).fold<
            Int,
            ReleaseNode?
        >(initial = null) { previous, step ->
            ReleaseNode(step = step, previous = previous)
        } ?: error("release chain should not be empty")
        val checkpoints = mutableListOf<Int>()

        val chainLength = DeepRecursiveFunction<ReleaseNode, Int> { node ->
            if (node.step == 2048 || node.step == 1024 || node.step == 1) {
                checkpoints += node.step
            }
            node.previous?.let { 1 + callRecursive(it) } ?: 1
        }(releaseChain)

        assertThat(chainLength).isEqualTo(2048)
        assertThat(checkpoints).containsExactly(2048, 1024, 1)
    }
}

private data class ReleaseNode(
    val step: Int,
    val previous: ReleaseNode?,
)

private class ModuleState {
    val events = mutableListOf<String>()

    val normalizedCoordinate: String by lazy {
        events += "lazy:normalize"
        "org.jetbrains.kotlin:kotlin-stdlib-common".replace("-stdlib-common", ".stdlib.common")
    }

    var releaseChannel: String by Delegates.observable("draft") { _, oldValue, newValue ->
        events += "channel:$oldValue->$newValue"
    }

    var priority: Int by Delegates.vetoable(0) { _, oldValue, newValue ->
        val accepted = newValue >= oldValue
        events += if (accepted) {
            "priority:accepted:$oldValue->$newValue"
        } else {
            "priority:rejected:$oldValue->$newValue"
        }
        accepted
    }
}
