/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package android.util

public object Log {
    private val loggedMessages: MutableList<Message> = mutableListOf()

    @JvmStatic
    public fun i(tag: String, message: String): Int {
        loggedMessages += Message(tag, message)
        return message.length
    }

    public fun clear(): Unit {
        loggedMessages.clear()
    }

    public fun messages(): List<Message> = loggedMessages.toList()

    public data class Message(public val tag: String, public val message: String)
}
