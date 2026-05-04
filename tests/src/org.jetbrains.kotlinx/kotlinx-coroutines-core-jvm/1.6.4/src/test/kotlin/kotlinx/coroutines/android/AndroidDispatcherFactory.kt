/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package kotlinx.coroutines.android

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.internal.MainDispatcherFactory

@OptIn(InternalCoroutinesApi::class)
public class AndroidDispatcherFactory : MainDispatcherFactory {
    override val loadPriority: Int = Int.MAX_VALUE

    override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher =
        AndroidFastServiceLoaderTestMainDispatcher

    override fun hintOnError(): String = "Android FastServiceLoader fixture failed"
}

private object AndroidFastServiceLoaderTestMainDispatcher : MainCoroutineDispatcher() {
    override val immediate: MainCoroutineDispatcher = this

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = false

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        block.run()
    }

    override fun toString(): String = "AndroidFastServiceLoaderTestMainDispatcher"
}
