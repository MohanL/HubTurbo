package unstable;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.loadui.testfx.controls.Commons.hasText;
import static ui.components.KeyboardShortcuts.*;

import org.junit.Before;
import org.junit.Test;
import org.loadui.testfx.GuiTest;

import guitests.UITest;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import prefs.Preferences;
import ui.IdGenerator;
import ui.TestController;
import ui.UI;
import ui.issuepanel.PanelControl;
import util.PlatformEx;

public class BoardTests extends UITest {

    private static final String boardNameInputId = IdGenerator.getBoardNameInputFieldIdReference();
    private static final String boardNameSaveButtonId = IdGenerator.getBoardNameSaveButtonIdReference();

    /**
     * The initial state is one panel with no filter, and no saved boards
     */
    private static void reset() {
        UI ui = TestController.getUI();
        ui.getPanelControl().closeAllPanels();
        ui.getPanelControl().createNewPanelAtStart();
        UI.prefs.clearAllBoards();
        ui.updateTitle();
    }

    @Before
    public void before() {
        PlatformEx.runAndWait(BoardTests::reset);
    }

    @Test
    public void boards_panelCount_creatingAndClosingPanels() {
        UI ui = TestController.getUI();
        PanelControl panelControl = ui.getPanelControl();

        press(CLOSE_PANEL);
        waitAndAssertEquals(0, panelControl::getPanelCount);
        press(CREATE_RIGHT_PANEL);
        waitAndAssertEquals(1, panelControl::getPanelCount);
        press(CREATE_LEFT_PANEL);
        waitAndAssertEquals(2, panelControl::getPanelCount);

        traverseHubTurboMenu("Panels", "Create");
        waitAndAssertEquals(3, panelControl::getPanelCount);
        traverseHubTurboMenu("Panels", "Create (Left)");
        waitAndAssertEquals(4, panelControl::getPanelCount);
        traverseHubTurboMenu("Panels", "Close");
        waitAndAssertEquals(3, panelControl::getPanelCount);
        traverseHubTurboMenu("Panels", "Close");
        waitAndAssertEquals(2, panelControl::getPanelCount);
    }

    @Test
    public void boards_saveDialog_willSaveWhenNoBoardOpen() {
        UI ui = TestController.getUI();
        PanelControl panelControl = ui.getPanelControl();

        // No board is open
        assertEquals(0, panelControl.getNumberOfSavedBoards());
        assertEquals(ui.getTitle(), getUiTitleWithOpenBoard("none"));

        // Saving when no board is open should prompt the user to save a new board
        traverseHubTurboMenu("Boards", "Save");
        type("Board 1");
        push(KeyCode.ESCAPE);
    }

    @Test
    public void boards_saveDialog_willNotSaveOnCancellation() {
        PanelControl panelControl = TestController.getUI().getPanelControl();

        traverseHubTurboMenu("Boards", "Save");
        type("Board 1");
        push(KeyCode.ESCAPE);

        // No board is saved on cancellation
        waitAndAssertEquals(0, panelControl::getNumberOfSavedBoards);
    }

    @Test
    public void boards_lastOpenedBoard_switchingWhenNoBoardIsOpen() {
        // Switching when no board is open should do nothing
        pushKeys(SWITCH_BOARD);
        assertFalse(UI.prefs.getLastOpenBoard().isPresent());
    }

    @Test
    public void boards_panelCount_boardsSaveSuccessfully() {
        UI ui = TestController.getUI();
        PanelControl panelControl = ui.getPanelControl();

        saveBoardWithName("Board 1");

        waitAndAssertEquals(1, panelControl::getNumberOfSavedBoards);
        assertEquals(1, panelControl.getPanelCount());
        assertEquals(ui.getTitle(), getUiTitleWithOpenBoard("Board 1"));
    }

    private void saveBoardWithName(String name) {
        traverseHubTurboMenu("Boards", "Save as");
        waitUntilNodeAppears(boardNameInputId);
        ((TextField) GuiTest.find(boardNameInputId)).setText(name);
        clickOn("OK");
    }

    @Test
    public void baords_lastOpenedBoard_cannotSwitchBoardWithOnlyOneSaved() {

        Preferences prefs = UI.prefs;

        saveBoardWithName("Board 1");

        // Remain at the same board
        press(SWITCH_BOARD);
        assertTrue(prefs.getLastOpenBoard().isPresent());
        assertEquals("Board 1", prefs.getLastOpenBoard().get());
    }

    @Test
    public void boards_lastOpenedBoard_canSwitchBoardWithOnlyOneSaved() {

        Preferences prefs = UI.prefs;

        saveBoardWithName("Board 1");
        saveBoardWithName("Board 2");

        assertTrue(prefs.getLastOpenBoard().isPresent());
        assertEquals("Board 2", prefs.getLastOpenBoard().get());

        // Wraps around to the first board
        press(SWITCH_BOARD);
        assertTrue(prefs.getLastOpenBoard().isPresent());
        assertEquals("Board 1", prefs.getLastOpenBoard().get());

        // Back to the second board
        press(SWITCH_BOARD);
        assertTrue(prefs.getLastOpenBoard().isPresent());
        assertEquals("Board 2", prefs.getLastOpenBoard().get());
    }

    @Test
    public void boards_validation_displaysFeedbackOnFailingValidation() {
        tryBoardName("");
        tryBoardName("   ");
        tryBoardName("   none  ");
    }

    private void tryBoardName(String name) {
        traverseHubTurboMenu("Boards", "Save as");
        waitUntilNodeAppears(boardNameInputId);
        ((TextField) GuiTest.find(boardNameInputId)).setText(name);
        assertTrue(GuiTest.find(boardNameSaveButtonId).isDisabled());
        pushKeys(KeyCode.ESCAPE);
        waitUntilNodeDisappears(boardNameInputId);
    }

    @Test
    public void boards_panelCount_boardsCanBeOpenedSuccessfully() {

        UI ui = TestController.getUI();
        PanelControl panelControl = ui.getPanelControl();

        // Board 1 has 1 panel, Board 2 has 2
        saveBoardWithName("Board 1");
        pushKeys(CREATE_RIGHT_PANEL);
        saveBoardWithName("Board 2");

        // We're at Board 2 now
        waitAndAssertEquals(2, panelControl::getPanelCount);
        assertEquals(ui.getTitle(), getUiTitleWithOpenBoard("Board 2"));

        traverseHubTurboMenu("Boards", "Open", "Board 1");

        // We've switched to Board 1
        waitAndAssertEquals(1, panelControl::getPanelCount);
        assertEquals(ui.getTitle(), getUiTitleWithOpenBoard("Board 1"));
    }

    @Test
    public void boards_panelCount_boardsCanBeSavedSuccessfully() {

        PanelControl panelControl = TestController.getUI().getPanelControl();

        saveBoardWithName("Board 1");
        pushKeys(CREATE_RIGHT_PANEL);
        awaitCondition(() -> 2 == panelControl.getPanelCount());

        traverseHubTurboMenu("Boards", "Open", "Board 1");

        // Abort saving changes to current board
        waitUntilNodeAppears("No");
        clickOn("No");

        // Without having saved, we lose the extra panel
        waitAndAssertEquals(1, panelControl::getPanelCount);

        pushKeys(CREATE_RIGHT_PANEL);
        traverseHubTurboMenu("Boards", "Save");

        // After saving, the panel is there
        waitAndAssertEquals(2, panelControl::getPanelCount);
    }

    @Test
    public void boards_panelCount_boardsCanBeDeletedSuccessfully() {

        UI ui = TestController.getUI();
        PanelControl panelControl = ui.getPanelControl();

        saveBoardWithName("Board 1");

        traverseHubTurboMenu("Boards", "Delete", "Board 1");
        waitUntilNodeAppears(hasText("OK"));
        clickOn("OK");

        // No board is open now
        assertEquals(0, panelControl.getNumberOfSavedBoards());
        assertEquals(ui.getTitle(), getUiTitleWithOpenBoard("none"));

    }

    @Test
    public void boards_panelCount_switchToFirstBoardWhenNoBoardIsOpen() {
        saveBoardWithName("Board 1");
        saveBoardWithName("Board 2");
        saveBoardWithName("Board 3");

        traverseHubTurboMenu("Boards", "Delete", "Board 3");
        waitUntilNodeAppears(hasText("OK"));
        clickOn("OK");

        // Switching board has no effect
        assertFalse(UI.prefs.getLastOpenBoard().isPresent());
        pushKeys(SWITCH_BOARD);

        // Abort saving changes to current board
        waitUntilNodeAppears("No");
        clickOn("No");

        assertTrue(UI.prefs.getLastOpenBoard().isPresent());
        assertEquals("Board 2", UI.prefs.getLastOpenBoard().get());
    }

    @Test
    public void boards_panelCount_promptToSaveWhenCurrentBoardIsDirty() {
        UI ui = TestController.getUI();
        PanelControl panelControl = ui.getPanelControl();

        saveBoardWithName("Board 1");
        saveBoardWithName("Board 2");
        pushKeys(CREATE_RIGHT_PANEL);
        awaitCondition(() -> 2 == panelControl.getPanelCount());

        pushKeys(SWITCH_BOARD);

        // Confirm saving changes to current board
        waitUntilNodeAppears("Yes");
        clickOn("Yes");

        traverseHubTurboMenu("Boards", "Open", "Board 2");
        waitAndAssertEquals(2, panelControl::getPanelCount);
    }

    @Test
    public void boards_panelCount_askForBoardNameWhenCurrentBoardHasNeverBeenSaved() {
        UI ui = TestController.getUI();
        PanelControl panelControl = ui.getPanelControl();

        pushKeys(CREATE_RIGHT_PANEL);
        pushKeys(CREATE_RIGHT_PANEL);
        awaitCondition(() -> 3 == panelControl.getPanelCount());

        traverseHubTurboMenu("Boards", "Save");
        waitUntilNodeAppears(boardNameInputId);
        ((TextField) GuiTest.find(boardNameInputId)).setText("Board 1");
        clickOn("OK");

        assertEquals("Board 1", UI.prefs.getLastOpenBoard().get());

        traverseHubTurboMenu("Boards", "Open", "Board 1");
        waitAndAssertEquals(3, panelControl::getPanelCount);
    }

    private static String getUiTitleWithOpenBoard(String boardName) {
        return String.format(UI.WINDOW_TITLE, "dummy/dummy", boardName);
    }

    @Test
    public void boards_panelCount_nothingHappensWhenNewBoardIsCancelled() {
        UI ui = TestController.getUI();
        PanelControl panelControl = ui.getPanelControl();

        assertEquals(0, panelControl.getNumberOfSavedBoards());
        assertEquals(1, panelControl.getPanelCount());

        traverseHubTurboMenu("Boards", "New");

        // Abort saving changes to current board
        waitUntilNodeAppears("No");
        clickOn("No");

        // Cancel new board action
        waitUntilNodeAppears("Cancel");
        clickOn("Cancel");
        assertEquals(0, panelControl.getNumberOfSavedBoards());
        assertEquals(1, panelControl.getPanelCount());
    }

    @Test
    public void boards_panelCount_newBoardCreated() {

        UI ui = TestController.getUI();
        PanelControl panelControl = ui.getPanelControl();

        traverseHubTurboMenu("Boards", "New");

        // Abort saving changes to current board
        waitUntilNodeAppears("No");
        clickOn("No");

        waitUntilNodeAppears(boardNameInputId);
        ((TextField) GuiTest.find(boardNameInputId)).setText("empty");
        waitUntilNodeAppears("OK");
        clickOn("OK");

        waitAndAssertEquals(1, panelControl::getPanelCount);
        waitAndAssertEquals(ui.getTitle(), () -> getUiTitleWithOpenBoard("empty"));
    }
}