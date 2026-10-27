/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises Kotlin singleton-object deserialization for replayable worker-control commands.
 * A worker consumes pause and resume signals using Kotlin object matching and archives the
 * commands so a replacement worker can replay the same dispatch decisions.
 */
public class SingletonWorkerControlTest {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
    @JsonSubTypes(
        JsonSubTypes.Type(value = Pause::class, name = "pause"),
        JsonSubTypes.Type(value = Resume::class, name = "resume"),
    )
    public interface WorkerCommand

    @JsonTypeName("pause")
    public object Pause : WorkerCommand

    @JsonTypeName("resume")
    public object Resume : WorkerCommand

    public class Worker {
        public var acceptingJobs: Boolean = true
            private set

        public fun apply(command: WorkerCommand): Boolean {
            when (command) {
                Pause -> acceptingJobs = false
                Resume -> acceptingJobs = true
                else -> error("Unrecognized worker-control command")
            }
            return acceptingJobs
        }
    }

    @Test
    @Timeout(60)
    public fun replaysSingletonSignalsAndRestoresDispatchDecisions(): Unit {
        val module: KotlinModule = KotlinModule.Builder()
            .enable(KotlinFeature.SingletonSupport)
            .build()
        val mapper: ObjectMapper = ObjectMapper().registerModule(module)
        val controlJson: String = """
            [
              {"command": "pause"},
              {"command": "resume"},
              {"command": "pause"}
            ]
        """.trimIndent()

        val commands: List<WorkerCommand> = mapper.readValue(controlJson)
        assertSame(Pause, commands[0])
        assertSame(Resume, commands[1])
        assertSame(Pause, commands[2])
        val worker: Worker = Worker()
        val dispatchDecisions: List<Boolean> = commands.map { command: WorkerCommand ->
            worker.apply(command)
        }
        assertEquals(listOf(false, true, false), dispatchDecisions)
        assertEquals(false, worker.acceptingJobs)

        val archivedJson: String = mapper.writerFor(jacksonTypeRef<List<WorkerCommand>>())
            .writeValueAsString(commands)
        val archived: JsonNode = mapper.readTree(archivedJson)
        assertEquals(mapper.readTree(controlJson), archived)
        val replayedCommands: List<WorkerCommand> = mapper.readValue(archivedJson)
        val replacementWorker: Worker = Worker()
        val replayedDecisions: List<Boolean> = replayedCommands.map { command: WorkerCommand ->
            replacementWorker.apply(command)
        }
        assertEquals(dispatchDecisions, replayedDecisions)
        assertEquals(false, replacementWorker.acceptingJobs)
        assertSame(Pause, replayedCommands.last())
    }
}
