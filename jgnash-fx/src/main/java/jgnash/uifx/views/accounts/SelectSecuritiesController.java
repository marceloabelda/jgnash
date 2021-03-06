/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2014 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.uifx.views.accounts;

import java.net.URL;
import java.util.Collections;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import jgnash.engine.Account;
import jgnash.engine.Engine;
import jgnash.engine.EngineFactory;
import jgnash.engine.SecurityNode;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.scene.control.ButtonBar;
import org.controlsfx.control.CheckListView;

/**
 * Controller for selecting allowed securities from a list.  If a security is used within the account, checked
 * state will be forced.
 * <p/>
 * A custom selection manager would be a better choice
 *
 * @author Craig Cavanaugh
 */
public class SelectSecuritiesController implements Initializable {

    @FXML
    private CheckListView<LockedDecorator> checkListView;

    @FXML
    private ButtonBar buttonBar;

    private boolean result = false;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {

        checkListView.setCellFactory(listView -> new LockedCheckBoxListCell(checkListView::getItemBooleanProperty));

        // Create and add the ok and cancel buttons to the button bar
        final Button okButton = new Button(resources.getString("Button.Ok"));
        final Button cancelButton = new Button(resources.getString("Button.Cancel"));

        ButtonBar.setButtonData(okButton, ButtonBar.ButtonData.OK_DONE);
        ButtonBar.setButtonData(cancelButton, ButtonBar.ButtonData.CANCEL_CLOSE);

        buttonBar.getButtons().addAll(okButton, cancelButton);

        okButton.setOnAction(event -> {
            result = true;
            ((Stage) okButton.getScene().getWindow()).close();
        });

        cancelButton.setOnAction(event -> {
            result = false;
            ((Stage) cancelButton.getScene().getWindow()).close();
        });

        checkListView.getCheckModel().getCheckedItems().addListener((ListChangeListener<LockedDecorator>) change -> forceLockedSecurities());
    }

    private void forceLockedSecurities() {
        // must be pushed later
        checkListView.getItems().stream().filter(LockedDecorator::isLocked).forEach(lockedDecorator ->
                Platform.runLater(() -> checkListView.getCheckModel().check(lockedDecorator)));
    }

    public void loadSecuritiesForAccount(final Account account) {
        final Engine engine = EngineFactory.getEngine(EngineFactory.DEFAULT);
        Objects.requireNonNull(engine);

        Set<SecurityNode> usedSecurities = Collections.<SecurityNode>emptySet();

        if (account != null) {
            usedSecurities = account.getUsedSecurities();
        }

        final ObservableList<LockedDecorator> items = checkListView.getItems();

        // Add all known securities to the list
        for (SecurityNode node : engine.getSecurities()) {
            if (usedSecurities.contains(node)) {
                items.add(new LockedDecorator(node, true));
            } else {
                items.add(new LockedDecorator(node, false));
            }
        }

        // Select the used securities
        if (account != null) {
            for (SecurityNode node : account.getSecurities()) {
                items.stream().filter(decorator -> decorator.securityNode.equals(node)).forEach(decorator ->
                        Platform.runLater(() -> checkListView.getCheckModel().check(decorator)));
            }
        }

        // Force selection of used account securities
        forceLockedSecurities();
    }

    public boolean getResult() {
        return result;
    }

    private static class LockedDecorator {

        private final boolean locked;

        private final SecurityNode securityNode;

        protected LockedDecorator(final SecurityNode securityNode, final boolean locked) {
            this.securityNode = securityNode;
            this.locked = locked;
        }

        @Override
        public String toString() {
            return securityNode.toString();
        }

        public boolean isLocked() {
            return locked;
        }
    }

    public Set<SecurityNode> getSelectedSecurities() {
        Set<SecurityNode> securityNodeSet = new TreeSet<>();

        securityNodeSet.addAll(checkListView.getCheckModel().getCheckedItems().stream().map(lockedDecorator -> lockedDecorator.securityNode).collect(Collectors.toList()));

        return securityNodeSet;
    }

    /**
     * Provides visual feedback that item is locked and may not be unchecked
     */
    private static class LockedCheckBoxListCell extends CheckBoxListCell<LockedDecorator> {

        public LockedCheckBoxListCell(final Callback<LockedDecorator, ObservableValue<Boolean>> callback) {
            super(callback);
        }

        @Override
        public void updateItem(final LockedDecorator item, final boolean empty) {
            super.updateItem(item, empty);  // required

            if (!empty) {
                if (item.isLocked()) {
                    setStyle("-fx-font-style:italic;");
                }
            }
        }
    }
}
