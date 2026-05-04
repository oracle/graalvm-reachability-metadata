/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjfx.javafx_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Test;

public class FXCollectionsInnerCheckedObservableListTest {

    @Test
    void addAllCollectionOverloadsCopyInputThroughTypedArrays() {
        ObservableList<Integer> checkedValues = FXCollections.checkedObservableList(
                FXCollections.observableArrayList(10, 40), Integer.class);

        boolean appended = checkedValues.addAll(List.of(50, 60));
        boolean inserted = checkedValues.addAll(1, List.of(20, 30));

        assertThat(appended).isTrue();
        assertThat(inserted).isTrue();
        assertThat(checkedValues).containsExactly(10, 20, 30, 40, 50, 60);
    }

    @Test
    void addAllVarargsCopiesInputThroughTypedArray() {
        ObservableList<Integer> checkedValues = FXCollections.checkedObservableList(
                FXCollections.observableArrayList(10), Integer.class);

        boolean changed = checkedValues.addAll(20, 30);

        assertThat(changed).isTrue();
        assertThat(checkedValues).containsExactly(10, 20, 30);
    }

    @Test
    void setAllOverloadsCopyInputThroughTypedArrays() {
        ObservableList<Integer> checkedValues = FXCollections.checkedObservableList(
                FXCollections.observableArrayList(10, 20), Integer.class);

        boolean changedFromVarargs = checkedValues.setAll(30, 40);
        boolean changedFromCollection = checkedValues.setAll(List.of(50, 60));

        assertThat(changedFromVarargs).isTrue();
        assertThat(changedFromCollection).isTrue();
        assertThat(checkedValues).containsExactly(50, 60);
    }
}
