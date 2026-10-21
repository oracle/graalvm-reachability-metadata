/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_core_jvm

import arrow.core.AtomicMemoizationCache
import arrow.core.ConcurrentMapMemoizationCache
import arrow.core.MemoizationCache
import arrow.core.MemoizedDeepRecursiveFunction
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Memoized deep recursion with `MemoizedDeepRecursiveFunction` and Arrow's memoization caches.
 *
 * A build tool estimates how long a module takes to build from scratch: its own compile time
 * plus the longest chain among its dependencies, since independent dependencies build in
 * parallel. Module graphs share dependencies heavily, so each module is evaluated once and
 * cached; and generated module chains can be tens of thousands deep, so the recursion runs on
 * the heap through `DeepRecursiveFunction` instead of the call stack. The planner works with
 * the default cache, an explicit `AtomicMemoizationCache`, a `ConcurrentMapMemoizationCache`
 * shared between worker threads, and its own `MemoizationCache` that counts hits.
 */
class BuildGraphMemoizedRecursionTest {

    data class Module(val name: String, val compileSeconds: Int, val dependsOn: List<String>)

    /** Application cache that remembers results and counts how often a lookup was served. */
    class CountingCache<K, V> : MemoizationCache<K, V> {
        private val entries: MutableMap<K, V> = LinkedHashMap()
        var hits: Int = 0
            private set

        override fun get(key: K): V? {
            val value: V? = entries[key]
            if (value != null) {
                hits++
            }
            return value
        }

        override fun set(key: K, value: V): V {
            entries[key] = value
            return value
        }

        fun keys(): Set<K> = entries.keys
    }

    class BuildPlanner(private val modules: Map<String, Module>) {
        val evaluated: MutableList<String> = mutableListOf()

        fun criticalPath(cache: MemoizationCache<String, Int>): DeepRecursiveFunction<String, Int> =
            MemoizedDeepRecursiveFunction(cache) { name: String ->
                val module: Module = modules.getValue(name)
                synchronized(evaluated) { evaluated.add(name) }
                var slowestDependency = 0
                for (dependency: String in module.dependsOn) {
                    slowestDependency = maxOf(slowestDependency, callRecursive(dependency))
                }
                module.compileSeconds + slowestDependency
            }

        fun criticalPathWithDefaultCache(): DeepRecursiveFunction<String, Int> =
            MemoizedDeepRecursiveFunction { name: String ->
                val module: Module = modules.getValue(name)
                evaluated.add(name)
                module.compileSeconds + (module.dependsOn.maxOfOrNull { callRecursive(it) } ?: 0)
            }
    }

    private val diamond: Map<String, Module> = listOf(
        Module("core", 5, emptyList()),
        Module("json", 3, listOf("core")),
        Module("http", 4, listOf("core")),
        Module("db", 7, listOf("core")),
        Module("api", 2, listOf("json", "http")),
        Module("worker", 6, listOf("json", "db")),
        Module("app", 1, listOf("api", "worker", "core")),
    ).associateBy { module: Module -> module.name }

    @Test
    fun sharedDependenciesAreEvaluatedOnce() {
        val planner = BuildPlanner(diamond)

        val buildTime: Int = planner.criticalPathWithDefaultCache()("app")

        // app(1) + worker(6) + db(7) + core(5)
        assertEquals(19, buildTime)
        assertEquals(diamond.keys, planner.evaluated.toSet())
        assertEquals(diamond.size, planner.evaluated.size)
    }

    @Test
    fun atomicCacheKeepsResultsAcrossQueries() {
        val planner = BuildPlanner(diamond)
        val criticalPath: DeepRecursiveFunction<String, Int> =
            planner.criticalPath(AtomicMemoizationCache())

        assertEquals(11, criticalPath("api"))
        assertEquals(listOf("api", "json", "core", "http"), planner.evaluated)

        assertEquals(18, criticalPath("worker"))
        assertEquals(19, criticalPath("app"))
        assertEquals(11, criticalPath("api"))
        assertEquals(listOf("api", "json", "core", "http", "worker", "db", "app"), planner.evaluated)
    }

    @Test
    fun generatedChainDeeperThanTheCallStackIsPlanned() {
        val depth = 50_000
        val chain: Map<String, Module> = (0 until depth).associate { index: Int ->
            val name = "gen-$index"
            val dependencies: List<String> = if (index == 0) emptyList() else listOf("gen-${index - 1}")
            name to Module(name, 1 + index % 3, dependencies)
        }
        val planner = BuildPlanner(chain)

        val store: ConcurrentHashMap<String, Int> = ConcurrentHashMap()

        val buildTime: Int =
            planner.criticalPath(ConcurrentMapMemoizationCache(store))("gen-${depth - 1}")

        val expected: Int = (0 until depth).sumOf { index: Int -> 1 + index % 3 }
        assertEquals(expected, buildTime)
        assertEquals(depth, planner.evaluated.size)
        assertEquals(depth, store.size)
        assertEquals(1, store.getValue("gen-0"))
    }

    @Test
    fun customCacheServesRepeatedLookups() {
        val planner = BuildPlanner(diamond)
        val cache: CountingCache<String, Int> = CountingCache()
        val criticalPath: DeepRecursiveFunction<String, Int> = planner.criticalPath(cache)

        assertEquals(19, criticalPath("app"))
        // core is reached again from http, db and app; json again from worker.
        assertEquals(4, cache.hits)
        assertEquals(diamond.keys, cache.keys())

        assertEquals(19, criticalPath("app"))
        assertEquals(5, cache.hits)
        assertEquals(diamond.size, planner.evaluated.size)
    }

    @Test
    fun concurrentCacheIsSharedByParallelWorkers() {
        val planner = BuildPlanner(diamond)
        val store: ConcurrentHashMap<String, Int> = ConcurrentHashMap()
        val criticalPath: DeepRecursiveFunction<String, Int> =
            planner.criticalPath(ConcurrentMapMemoizationCache(store))
        val targets: List<String> = listOf("app", "api", "worker", "db", "http", "json", "core")
        val executor: ExecutorService = Executors.newFixedThreadPool(4)

        val results: Map<String, Int> = try {
            val futures: List<Pair<String, Future<Int>>> = targets.map { target: String ->
                target to executor.submit(Callable { criticalPath(target) })
            }
            futures.associate { (target: String, future: Future<Int>) ->
                target to future.get(30, TimeUnit.SECONDS)
            }
        } finally {
            executor.shutdown()
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS))
        }

        val expected: Map<String, Int> = mapOf(
            "core" to 5,
            "json" to 8,
            "http" to 9,
            "db" to 12,
            "api" to 11,
            "worker" to 18,
            "app" to 19,
        )
        assertEquals(expected, results)
        assertEquals(expected, store.toMap())
        assertEquals(diamond.keys, planner.evaluated.toSet())
    }
}
