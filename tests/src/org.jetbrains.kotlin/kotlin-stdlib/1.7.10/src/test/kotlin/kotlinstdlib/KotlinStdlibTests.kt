package kotlinstdlib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test


class KotlinStdlibTests {
  @Test
  fun testCoroutine() {
    val updated = AtomicBoolean(false)
    assertFalse(updated.get())
    CoroutineScope(Dispatchers.Default).launch {
      delay(500)
      updated.set(true)
    }
    Thread.sleep(1000)
    assertTrue(updated.get())
  }

  @Test
  fun testKotlinRandom() {
    val randomValue = Random.nextInt(0, 10)
    assertTrue { randomValue in 0 until 10 }
  }
}

