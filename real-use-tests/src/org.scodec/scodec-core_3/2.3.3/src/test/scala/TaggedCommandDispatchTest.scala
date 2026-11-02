/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package scodec_core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.{discriminated, uint8, uint16, utf8, variableSizeBytes}

/** Exercises tagged-union command dispatch for a device control client. */
class TaggedCommandDispatchTest {
  private sealed trait Command
  private case class Heartbeat(sequence: Int) extends Command
  private case class SetMode(mode: String) extends Command

  private val heartbeatCodec: Codec[Heartbeat] = uint16.xmap(Heartbeat.apply, _.sequence)
  private val setModeCodec: Codec[SetMode] =
    variableSizeBytes(uint8, utf8).xmap(SetMode.apply, _.mode)

  private val commandCodec: Codec[Command] =
    discriminated[Command]
      .by(uint8)
      .typecase(1, heartbeatCodec)
      .typecase(2, setModeCodec)

  @Test
  @Timeout(60)
  def encodesAndDispatchesDifferentCommandVariants(): Unit = {
    val heartbeat: Command = Heartbeat(300)
    val modeChange: Command = SetMode("safe")

    val encodedHeartbeat: ByteVector = commandCodec.encode(heartbeat).require.toByteVector
    val encodedModeChange: ByteVector = commandCodec.encode(modeChange).require.toByteVector

    assertEquals("01012c", encodedHeartbeat.toHex)
    assertEquals("020473616665", encodedModeChange.toHex)
    assertEquals(heartbeat, commandCodec.decode(encodedHeartbeat.toBitVector).require.value)
    assertEquals(modeChange, commandCodec.decode(encodedModeChange.toBitVector).require.value)
  }

  @Test
  @Timeout(60)
  def rejectsACommandWithAnUnknownDispatchTag(): Unit = {
    val unknownCommand: ByteVector = ByteVector.fromValidHex("0300")

    val decoded = commandCodec.decode(unknownCommand.toBitVector)

    assertFalse(decoded.isSuccessful)
  }
}
