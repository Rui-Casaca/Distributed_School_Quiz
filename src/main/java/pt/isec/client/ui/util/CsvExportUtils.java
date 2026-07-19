package pt.isec.client.ui.util;

import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.Question;
import pt.isec.common.util.Log;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Utility functions to export question answers to CSV in the format defined by the assignment.
 */
public final class CsvExportUtils {

    private CsvExportUtils() {}

    /**
     * Exports the answers for a given question to a CSV file.
     * <p>
     * The method shows a {@link FileChooser} to the user and writes the file in UTF-8 with BOM
     * (for better Excel compatibility on Windows). It also shows success/error alerts to the user.
     *
     * @param ownerWindow owner window for dialogs (may be {@code null})
     * @param q           question whose answers will be exported
     * @param answers     list of answers to export
     */
    // TODO Docente: exportação dos detalhes associados a uma pergunta expirada para um ficheiro CSV
    public static void exportAnswersToCsv(Window ownerWindow,
                                          Question q,
                                          List<Answer> answers) {

        if (q == null) {
            AlertUtils.showError(ownerWindow, "Exportar CSV", "Pergunta inválida.");
            return;
        }

        if (answers == null || answers.isEmpty()) {
            AlertUtils.showError(ownerWindow, "Exportar CSV", "Ainda não existem respostas para esta pergunta.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar ficheiro CSV");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Ficheiros CSV", "*.csv")
        );

        String baseName = (q.getAccessCode() != null && !q.getAccessCode().isBlank())
                ? q.getAccessCode()
                : "pergunta";
        fileChooser.setInitialFileName("pergunta_" + baseName + ".csv");

        var file = fileChooser.showSaveDialog(ownerWindow);
        if (file == null) {
            // user cancelled
            return;
        }

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

        try (OutputStream fos = Files.newOutputStream(file.toPath())) {
            // UTF-8 BOM for Excel on Windows
            fos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            try (BufferedWriter writer =
                         new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {

                // 1st line: question header
                writer.write("\"dia\";\"hora inicial\";\"hora final\";\"enunciado da pergunta\";\"opção certa\"");
                writer.newLine();

                // 2nd line: question data
                String dia = q.getStartAt().toLocalDate().format(dateFmt);
                String horaInicial = q.getStartAt().toLocalTime().format(timeFmt);
                String horaFinal = q.getEndAt().toLocalTime().format(timeFmt);
                String enunciado = escapeCsv(q.getStatement());
                String opcaoCerta = q.getCorrectOption() != null
                        ? q.getCorrectOption().name().toLowerCase()
                        : "";

                writer.write("\"" + dia + "\";\"" + horaInicial + "\";\"" + horaFinal + "\";\""
                        + enunciado + "\";\"" + opcaoCerta + "\"");
                writer.newLine();
                writer.newLine();

                // Options block
                writer.write("\"opção\";\"texto da opção\"");
                writer.newLine();

                if (q.getOptions() != null) {
                    for (Option opt : q.getOptions()) {
                        if (opt == null) {
                            continue;
                        }
                        String letra = opt.getLetter() != null
                                ? opt.getLetter().name().toLowerCase()
                                : "";
                        String texto = escapeCsv(opt.getText());
                        writer.write("\"" + letra + "\";\"" + texto + "\"");
                        writer.newLine();
                    }
                }
                writer.newLine();

                // Answers block
                writer.write("\"número de estudante\";\"nome\";\"e-mail\";\"resposta\"");
                writer.newLine();

                for (Answer a : answers) {
                    if (a == null) {
                        continue;
                    }

                    String numero = a.getStudentNumber() == null
                            ? ""
                            : a.getStudentNumber().toString();
                    String nome = escapeCsv(a.getStudentName());
                    String email = escapeCsv(a.getStudentEmail());
                    String resp = a.getSelectedOption() != null
                            ? a.getSelectedOption().name().toLowerCase()
                            : "";

                    writer.write("\"" + numero + "\";\"" + nome + "\";\"" + email + "\";\"" + resp + "\"");
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            Log.error(CsvExportUtils.class, "CSV não exportado: " + e.getMessage(), e);
            Platform.runLater(() ->
                    AlertUtils.showError(ownerWindow, "Exportar CSV", "Erro ao guardar o ficheiro!")
            );
            return;
        }

        Log.info(CsvExportUtils.class, "CSV exportado!");
        Platform.runLater(() ->
                AlertUtils.showInfo(ownerWindow, "Exportar CSV", "Ficheiro CSV gravado com sucesso")
        );
    }

    /**
     * Escapes quotes in a CSV field by doubling them.
     *
     * @param s raw string
     * @return escaped string (never {@code null})
     */
    private static String escapeCsv(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\"", "\"\"");
    }
}
