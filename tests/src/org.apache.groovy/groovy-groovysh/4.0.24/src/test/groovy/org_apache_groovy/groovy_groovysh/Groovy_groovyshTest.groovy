/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_groovysh

import org.apache.groovy.groovysh.BufferManager
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

public class Groovy_groovyshTest {
    @Test
    void keepsSeparateContentsForSelectableShellBuffers() {
        BufferManager manager = new BufferManager()

        assertThat(manager.size()).isEqualTo(1)
        assertThat(manager.selected).isZero()
        assertThat(manager.current()).isEmpty()

        manager.current().add('first buffer line')
        int secondIndex = manager.create(true)
        manager.current().add('second buffer line')

        assertThat(secondIndex).isEqualTo(1)
        assertThat(manager.selected).isEqualTo(secondIndex)
        assertThat(manager.current()).containsExactly('second buffer line')

        manager.select(0)
        assertThat(manager.current()).containsExactly('first buffer line')

        manager.updateSelected(['replacement line'])
        assertThat(manager.current()).containsExactly('replacement line')

        manager.clearSelected()
        assertThat(manager.current()).isEmpty()

        manager.select(secondIndex)
        assertThat(manager.current()).containsExactly('second buffer line')
    }

    @Test
    void createsUnselectedBuffersAndDeletesSelectedBufferByMovingBackward() {
        BufferManager manager = new BufferManager()
        manager.current().add('initial buffer line')

        int unselectedIndex = manager.create(false)

        assertThat(unselectedIndex).isEqualTo(1)
        assertThat(manager.selected).isZero()
        assertThat(manager.size()).isEqualTo(2)

        manager.select(unselectedIndex)
        manager.current().add('unselected buffer line')
        int selectedIndex = manager.create(true)
        manager.current().add('selected buffer line')

        assertThat(selectedIndex).isEqualTo(2)
        assertThat(manager.selected).isEqualTo(selectedIndex)

        manager.deleteSelected()

        assertThat(manager.size()).isEqualTo(2)
        assertThat(manager.selected).isEqualTo(1)
        assertThat(manager.current()).containsExactly('unselected buffer line')

        manager.deleteSelected()

        assertThat(manager.size()).isEqualTo(1)
        assertThat(manager.selected).isZero()
        assertThat(manager.current()).containsExactly('initial buffer line')
    }
}
