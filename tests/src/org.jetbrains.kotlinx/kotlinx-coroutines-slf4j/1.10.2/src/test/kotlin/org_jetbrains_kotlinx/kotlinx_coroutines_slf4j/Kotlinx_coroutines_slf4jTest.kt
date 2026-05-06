/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_slf4j

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

public class Kotlinx_coroutines_slf4jTest {
    @AfterEach
    fun clearMappedDiagnosticContext(): Unit {
        MDC.clear()
    }

    @Test
    fun mdcContextPropagatesCapturedValuesAcrossDispatcherSuspensions(): Unit = runBlocking {
        MDC.put(REQUEST_ID, "request-1")
        MDC.put(TENANT, "tenant-a")
        val parentContext: Map<String, String>? = MDC.getCopyOfContextMap()

        val observed = withTimeout(5_000) {
            withContext(Dispatchers.Default + MDCContext() + CoroutineName("mdc-propagation")) {
                assertThat(coroutineContext[CoroutineName]?.name).isEqualTo("mdc-propagation")
                val beforeYield: Map<String, String>? = MDC.getCopyOfContextMap()
                yield()
                val afterYield: Map<String, String>? = MDC.getCopyOfContextMap()
                listOf(beforeYield, afterYield)
            }
        }

        assertThat(observed[0]).containsEntry(REQUEST_ID, "request-1").containsEntry(TENANT, "tenant-a")
        assertThat(observed[1]).containsEntry(REQUEST_ID, "request-1").containsEntry(TENANT, "tenant-a")
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(parentContext)
    }

    @Test
    fun defaultMdcContextCapturesSnapshotAtConstructionTime(): Unit = runBlocking {
        MDC.put(REQUEST_ID, "captured")
        val element: MDCContext = MDCContext()

        MDC.put(REQUEST_ID, "parent-updated")
        MDC.put(TENANT, "parent-only")
        val parentContext: Map<String, String>? = MDC.getCopyOfContextMap()

        val coroutineContextMap: Map<String, String>? = withTimeout(5_000) {
            withContext(Dispatchers.Default + element) {
                yield()
                MDC.getCopyOfContextMap()
            }
        }

        assertThat(element.contextMap)
            .containsEntry(REQUEST_ID, "captured")
            .doesNotContainKey(TENANT)
        assertThat(coroutineContextMap)
            .containsEntry(REQUEST_ID, "captured")
            .doesNotContainKey(TENANT)
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(parentContext)
    }

    @Test
    fun mdcChangesInsideCoroutineAreRestoredToCapturedSnapshotAfterSuspension(): Unit = runBlocking {
        MDC.put(REQUEST_ID, "captured")

        withTimeout(5_000) {
            withContext(Dispatchers.Default + MDCContext()) {
                assertThat(MDC.get(REQUEST_ID)).isEqualTo("captured")

                MDC.put(REQUEST_ID, "not-captured")
                MDC.put(LOCAL_ONLY, "temporary")
                assertThat(MDC.get(REQUEST_ID)).isEqualTo("not-captured")
                assertThat(MDC.get(LOCAL_ONLY)).isEqualTo("temporary")

                yield()

                assertThat(MDC.get(REQUEST_ID)).isEqualTo("captured")
                assertThat(MDC.get(LOCAL_ONLY)).isNull()
            }
        }

        assertThat(MDC.get(REQUEST_ID)).isEqualTo("captured")
        assertThat(MDC.get(LOCAL_ONLY)).isNull()
    }

    @Test
    fun nestedMdcContextCanCaptureUpdatedValuesAndThenRestoreOuterSnapshot(): Unit = runBlocking {
        MDC.put(REQUEST_ID, "outer")
        val parentContext: Map<String, String>? = MDC.getCopyOfContextMap()

        withTimeout(5_000) {
            withContext(Dispatchers.Default + MDCContext()) {
                assertThat(MDC.get(REQUEST_ID)).isEqualTo("outer")

                MDC.put(REQUEST_ID, "inner-captured")
                withContext(MDCContext()) {
                    yield()
                    assertThat(MDC.get(REQUEST_ID)).isEqualTo("inner-captured")
                }

                assertThat(MDC.get(REQUEST_ID)).isEqualTo("outer")
                yield()
                assertThat(MDC.get(REQUEST_ID)).isEqualTo("outer")
            }
        }

        assertThat(MDC.getCopyOfContextMap()).isEqualTo(parentContext)
    }

    @Test
    fun explicitEmptyContextClearsMdcWithinCoroutineAndRestoresParentAfterwards(): Unit = runBlocking {
        MDC.put(REQUEST_ID, "parent")
        MDC.put(TENANT, "parent-tenant")
        val parentContext: Map<String, String>? = MDC.getCopyOfContextMap()

        withTimeout(5_000) {
            withContext(Dispatchers.Default + MDCContext(emptyMap())) {
                assertThat(MDC.get(REQUEST_ID)).isNull()
                assertThat(MDC.get(TENANT)).isNull()

                MDC.put(LOCAL_ONLY, "should-disappear")
                assertThat(MDC.get(LOCAL_ONLY)).isEqualTo("should-disappear")

                yield()

                assertThat(MDC.get(LOCAL_ONLY)).isNull()
                assertThat(MDC.getCopyOfContextMap()).isEmpty()
            }
        }

        assertThat(MDC.getCopyOfContextMap()).isEqualTo(parentContext)
    }

    @Test
    fun explicitContextMapOverridesCurrentMdcOnlyForCoroutineScope(): Unit = runBlocking {
        MDC.put(REQUEST_ID, "parent")
        val explicitContext: Map<String, String> = mapOf(
            REQUEST_ID to "explicit",
            TENANT to "tenant-b",
        )
        val element = MDCContext(explicitContext)

        val coroutineValues = withTimeout(5_000) {
            withContext(Dispatchers.Default + element) {
                yield()
                MDC.getCopyOfContextMap()
            }
        }

        assertThat(element.contextMap).isEqualTo(explicitContext)
        assertThat(coroutineValues).containsEntry(REQUEST_ID, "explicit").containsEntry(TENANT, "tenant-b")
        assertThat(MDC.get(REQUEST_ID)).isEqualTo("parent")
        assertThat(MDC.get(TENANT)).isNull()
    }

    @Test
    fun concurrentCoroutinesKeepIndependentMdcSnapshots(): Unit = runBlocking {
        val firstContext = MDCContext(mapOf(REQUEST_ID to "first", TENANT to "one"))
        val secondContext = MDCContext(mapOf(REQUEST_ID to "second", TENANT to "two"))

        val first = async(Dispatchers.Default + firstContext) {
            yield()
            mapOf(REQUEST_ID to MDC.get(REQUEST_ID), TENANT to MDC.get(TENANT))
        }
        val second = async(Dispatchers.Default + secondContext) {
            yield()
            mapOf(REQUEST_ID to MDC.get(REQUEST_ID), TENANT to MDC.get(TENANT))
        }

        assertThat(withTimeout(5_000) { first.await() })
            .containsEntry(REQUEST_ID, "first")
            .containsEntry(TENANT, "one")
        assertThat(withTimeout(5_000) { second.await() })
            .containsEntry(REQUEST_ID, "second")
            .containsEntry(TENANT, "two")
        assertThat(MDC.getCopyOfContextMap()).isNull()
    }

    @Test
    fun mdcContextElementIsAvailableByCoroutineContextKey(): Unit {
        val element = MDCContext(mapOf(REQUEST_ID to "keyed"))
        val context: CoroutineContext = element + CoroutineName("named")

        assertThat(context[MDCContext]).isSameAs(element)
        assertThat(context[MDCContext.Key]).isSameAs(element)
        assertThat(context[CoroutineName]?.name).isEqualTo("named")
        assertThat(element.contextMap).containsEntry(REQUEST_ID, "keyed")
    }

    @Test
    fun slf4jLogEventsIncludeCoroutineMdcAfterSuspension(): Unit = runBlocking {
        val logger = LoggerFactory.getLogger(
            "org_jetbrains_kotlinx.kotlinx_coroutines_slf4j.slf4jLogEventsIncludeCoroutineMdcAfterSuspension",
        ) as Logger
        val appender = object : ListAppender<ILoggingEvent>() {
            override fun append(eventObject: ILoggingEvent): Unit {
                eventObject.prepareForDeferredProcessing()
                super.append(eventObject)
            }
        }
        appender.name = "coroutine-mdc-list-appender"
        appender.start()
        val previousLevel: Level? = logger.level
        val previousAdditive: Boolean = logger.isAdditive
        logger.level = Level.INFO
        logger.isAdditive = false
        logger.addAppender(appender)

        try {
            withTimeout(5_000) {
                withContext(Dispatchers.Default + MDCContext(mapOf(REQUEST_ID to "logged", TENANT to "tenant-log"))) {
                    yield()
                    LoggerFactory.getLogger(logger.name).info("coroutine log event")
                }
            }

            assertThat(appender.list).hasSize(1)
            val event: ILoggingEvent = appender.list.single()
            assertThat(event.formattedMessage).isEqualTo("coroutine log event")
            assertThat(event.mdcPropertyMap)
                .containsEntry(REQUEST_ID, "logged")
                .containsEntry(TENANT, "tenant-log")
        } finally {
            logger.detachAppender(appender)
            appender.stop()
            logger.level = previousLevel
            logger.isAdditive = previousAdditive
        }
    }

    private companion object {
        private const val REQUEST_ID: String = "requestId"
        private const val TENANT: String = "tenant"
        private const val LOCAL_ONLY: String = "localOnly"
    }
}
