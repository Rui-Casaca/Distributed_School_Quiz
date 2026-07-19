package pt.isec.client.ui.util.dialogs;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import pt.isec.client.ui.util.AlertUtils;
import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.OptionLetter;
import pt.isec.common.model.question.Question;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable dialogs for the student side.
 * <p>
 * Controllers only pass callbacks (DTOs, etc.) to these helpers.
 */
public final class StudentDialogs {

    private StudentDialogs() {
    }

    // --------------------------------------------------------------
    //  QUESTION CODE INPUT
    // --------------------------------------------------------------

    /**
     * Shows a dialog to enter the question access code.
     * <p>
     * When the user confirms, {@code onCodeEntered} is called with the trimmed code.
     *
     * @param owner         window owner for modality
     * @param onCodeEntered callback invoked with the entered code
     */
    public static void showEnterQuestionCodeDialog(Window owner,
                                                   Consumer<String> onCodeEntered) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }

        dialog.setTitle("Responder Pergunta");
        dialog.setHeaderText("Insira o código da pergunta");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        Label label = new Label("Código da Pergunta:");
        TextField codeField = new TextField();
        codeField.setPromptText("Código (ex: ABC123)");
        codeField.setPrefWidth(250);

        content.getChildren().addAll(label, codeField);
        dialog.getDialogPane().setContent(content);

        ButtonType searchButtonType =
                new ButtonType("Buscar Pergunta", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(searchButtonType, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != searchButtonType) {
                return;
            }
            String code = codeField.getText();
            if (code == null || code.trim().isEmpty()) {
                AlertUtils.showError(owner, "Erro", "Código da pergunta é obrigatório.");
                return;
            }
            if (onCodeEntered != null) {
                onCodeEntered.accept(code.trim());
            }
        });
    }

    // --------------------------------------------------------------
    //  ANSWER QUESTION
    // --------------------------------------------------------------

    /**
     * Shows a question dialog with options for the student to choose.
     * <p>
     * On confirmation, calls {@code onSubmit} with a populated {@link SubmitAnswerDTO}
     * (questionId, studentId, selected option).
     *
     * @param owner     window owner
     * @param question  question to show
     * @param studentId student identifier
     * @param onSubmit  callback invoked when the user submits an answer
     */
    public static void showAnswerQuestionDialog(Window owner,
                                                Question question,
                                                Integer studentId,
                                                Consumer<SubmitAnswerDTO> onSubmit) {
        if (studentId == null) {
            AlertUtils.showError(owner, "Erro",
                    "Sessão inválida. Faça login novamente.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }

        dialog.setTitle("Pergunta - " + question.getAccessCode());
        dialog.setHeaderText(question.getStatement());

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        ToggleGroup group = new ToggleGroup();
        List<RadioButton> radioButtons = new ArrayList<>();

        List<Option> opts = question.getOptions();
        if (opts != null) {
            for (Option opt : opts) {
                RadioButton rb = new RadioButton(
                        opt.getLetter().name() + ") " + opt.getText()
                );
                rb.setToggleGroup(group);
                radioButtons.add(rb);
            }
        }

        VBox optionsBox = new VBox(10);
        optionsBox.getChildren().addAll(radioButtons);
        content.getChildren().add(optionsBox);
        dialog.getDialogPane().setContent(content);

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Submeter Resposta");

        okButton.setOnAction(ev -> {
            if (group.getSelectedToggle() == null) {
                AlertUtils.showError(owner, "Erro",
                        "Selecione uma opção antes de submeter.");
                ev.consume();
                return;
            }

            RadioButton selected = (RadioButton) group.getSelectedToggle();
            String answerLetter = selected.getText().substring(0, 1);
            OptionLetter selectedOption;

            try {
                selectedOption = OptionLetter.valueOf(answerLetter);
            } catch (Exception ex) {
                AlertUtils.showError(owner, "Erro",
                        "Opção selecionada inválida.");
                ev.consume();
                return;
            }

            if (onSubmit != null) {
                SubmitAnswerDTO dto = new SubmitAnswerDTO(
                        question.getId(),
                        studentId,
                        selectedOption
                );
                onSubmit.accept(dto);
            }
        });

        dialog.showAndWait();
    }

    // --------------------------------------------------------------
    //  ANSWER HISTORY
    // --------------------------------------------------------------

    /**
     * Shows a dialog with the student's answer history.
     * <p>
     * The dialog allows the user to apply a local filter on the records displayed:
     * <ul>
     *     <li>"Todas"      - all answers</li>
     *     <li>"Corretas"   - only correct answers</li>
     *     <li>"Incorretas" - only incorrect answers</li>
     * </ul>
     * The initial filter is defined by the {@code currentFilter} parameter and,
     * whenever the user changes the filter in the ComboBox, the
     * {@code onFilterChanged} callback is invoked with the new value.
     *
     * @param owner           parent window of the dialog (may be {@code null})
     * @param history         list of answers to display; if {@code null} or empty,
     *                        a simple message is shown instead of the table
     * @param currentFilter   filter currently selected in the controller
     *                        (for example, "Todas", "Corretas" or "Incorretas");
     *                        if {@code null} or not present in the ComboBox items,
     *                        "Todas" is used as default
     * @param onFilterChanged callback invoked whenever the user changes the filter
     *                        in the ComboBox; receives the newly selected filter;
     *                        may be {@code null}
     */
    public static void showHistoryDialog(Window owner,
                                         List<Answer> history,
                                         String currentFilter,
                                         Consumer<String> onFilterChanged) {
        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }

        dialog.setTitle("Histórico de Respostas");
        dialog.setHeaderText("Perguntas respondidas");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setPrefWidth(600);

        if (history == null || history.isEmpty()) {
            // No history available: show a simple informative label
            Label noDataLabel = new Label("Ainda não respondeu a nenhuma pergunta.");
            noDataLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 14;");
            content.getChildren().add(noDataLabel);
        } else {
            // ----------------- Filters (ComboBox) -----------------
            HBox filters = new HBox(10);
            filters.setAlignment(Pos.CENTER_LEFT);

            Label filterLabel = new Label("Filtrar:");
            filterLabel.setStyle("-fx-font-weight: bold;");

            ComboBox<String> filterCombo = new ComboBox<>();
            // Available filters in the UI (Portuguese labels)
            filterCombo.getItems().addAll("Todas", "Corretas", "Incorretas");

            // Initial filter coming from the controller
            String initialFilter = (currentFilter != null ? currentFilter : "Todas");
            if (!filterCombo.getItems().contains(initialFilter)) {
                initialFilter = "Todas";
            }
            filterCombo.setValue(initialFilter);

            filters.getChildren().addAll(filterLabel, filterCombo);

            // ----------------- Table -----------------
            // Keep the original history list so we can re-apply filters at any time
            List<Answer> originalHistory = new ArrayList<>(history);

            TableView<Answer> table = new TableView<>();
            table.setPrefHeight(300);
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

            TableColumn<Answer, String> dateCol = new TableColumn<>("Data/Hora");
            dateCol.setCellValueFactory(data ->
                    new javafx.beans.property.SimpleStringProperty(
                            data.getValue().getAnsweredAt()
                                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    )
            );

            TableColumn<Answer, String> questionCol = new TableColumn<>("Pergunta");
            questionCol.setCellValueFactory(data ->
                    new javafx.beans.property.SimpleStringProperty(
                            data.getValue().getQuestionStatement() != null
                                    ? data.getValue().getQuestionStatement()
                                    : String.valueOf(data.getValue().getQuestionId())
                    )
            );

            TableColumn<Answer, String> answerCol = new TableColumn<>("Resposta");
            answerCol.setCellValueFactory(data ->
                    new javafx.beans.property.SimpleStringProperty(
                            data.getValue().getSelectedOption().name()
                    )
            );

            TableColumn<Answer, String> resultCol = new TableColumn<>("Correta?");
            resultCol.setCellValueFactory(data ->
                    new javafx.beans.property.SimpleStringProperty(
                            data.getValue().isCorrect() ? "Sim" : "Não"
                    )
            );

            table.getColumns().addAll(dateCol, questionCol, answerCol, resultCol);

            content.getChildren().addAll(filters, table);

            // ----------------- Logic to apply filter to the table -----------------
            Consumer<String> applyFilterToTable = sel -> {
                table.getItems().clear();
                table.getSortOrder().clear();

                switch (sel) {
                    case "Corretas":
                        // Only correct answers
                        for (Answer a : originalHistory) {
                            if (a.isCorrect()) {
                                table.getItems().add(a);
                            }
                        }
                        break;

                    case "Incorretas":
                        // Only incorrect answers
                        for (Answer a : originalHistory) {
                            if (!a.isCorrect()) {
                                table.getItems().add(a);
                            }
                        }
                        break;

                    case "Todas":
                    default:
                        // Default behavior: show all answers
                        table.getItems().addAll(originalHistory);
                        break;
                }

                // Optional: always sort by date (most recent first)
                if (!table.getItems().isEmpty()) {
                    dateCol.setSortType(TableColumn.SortType.DESCENDING);
                    table.getSortOrder().add(dateCol);
                    table.sort();
                }
            };

            // Apply the initial filter coming from the controller
            applyFilterToTable.accept(initialFilter);

            // ----------------- ComboBox listener -----------------
            filterCombo.valueProperty().addListener((_, _, newVal) -> {
                String sel = (newVal != null ? newVal : "Todas");

                // Update table (UI)
                applyFilterToTable.accept(sel);

                // Notify controller about the new filter (state)
                if (onFilterChanged != null) {
                    onFilterChanged.accept(sel);
                }
            });
        }

        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

}
