package pt.isec.server.services.question;

import pt.isec.common.dto.answer.SubmitAnswerDTO;
import pt.isec.common.dto.answer.ViewAnswersDTO;
import pt.isec.common.messages.MessageType;
import pt.isec.common.messages.TcpMessage;
import pt.isec.common.model.question.Answer;
import pt.isec.common.model.question.OptionLetter;
import pt.isec.server.core.IQuestionAnswerContext;
import pt.isec.server.db.DbCommands;
import pt.isec.common.util.Log;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service that handles answer submission and queries.
 */
@SuppressWarnings("ClassCanBeRecord")
public class AnswerService implements IAnswerService {
    private final IQuestionAnswerContext context;
    private final DbCommands dbCommands;

    /**
     * Creates a new {@link AnswerService}.
     *
     * @param context    question/answer context used for replication and notifications
     * @param dbCommands database access helper
     */
    public AnswerService(IQuestionAnswerContext context, DbCommands dbCommands) {
        this.context = context;
        this.dbCommands = dbCommands;
    }

    /**
     * Registers a new answer and updates replication.
     *
     * @param dto answer data
     * @return {@code true} if successfully recorded
     */
    @Override
    public boolean submitAnswer(SubmitAnswerDTO dto)  {
        Integer questionId = dto.questionId();
        Integer studentId = dto.studentId();
        OptionLetter selected = dto.selectedOption();
        LocalDateTime now = LocalDateTime.now();

        // validate question (existence + active period) and retrieve teacher_id
        Map<String, Object> q = dbCommands.selectOne(
                "SELECT teacher_id, correct_option, start_at, end_at FROM question WHERE id = ?",
                questionId
        );
        if (q == null) {
            throw new IllegalArgumentException("Pergunta inexistente");
        }

        LocalDateTime startAt = LocalDateTime.parse((String) q.get("start_at"));
        LocalDateTime endAt = LocalDateTime.parse((String) q.get("end_at"));
        if (now.isBefore(startAt) || now.isAfter(endAt)) {
            throw new IllegalStateException("Pergunta fora do período de disponibilidade");
        }

        Map<String, Object> existing = dbCommands.selectOne(
                "SELECT 1 AS one FROM answer WHERE student_id = ? AND question_id = ? LIMIT 1",
                studentId, questionId
        );
        if (existing != null) {
            throw new IllegalStateException("Já respondeu a esta pergunta.");
        }

        // insert answer
        dbCommands.executeUpdate(
                "INSERT INTO answer (student_id, question_id, chosen_option, created_at) VALUES (?, ?, ?, ?)",
                studentId, questionId, selected.name(), now.toString()
        );

        // incremental replication for backup servers
        context.queue().add(Collections.singletonList(
                "INSERT INTO answer (student_id, question_id, chosen_option, created_at) VALUES (" +
                        studentId + ", " + questionId + ", '" + selected.name() + "', '" + now + "');"
        ));

        // Try to notify the owning teacher (if connected)
        try {
            Object rawTeacherId = q.get("teacher_id");
            Long teacherId = null;

            if (rawTeacherId instanceof Number n) {
                teacherId = n.longValue();
            } else if (rawTeacherId instanceof String s && !s.isBlank()) {
                teacherId = Long.parseLong(s);
            }

            if (teacherId != null) {
                TcpMessage<Integer> notify =
                        new TcpMessage<>(MessageType.ANSWER_SUBMITTED, questionId, Integer.class);
                context.sendToUser(teacherId, notify);

                Log.info(AnswerService.class,
                        "Real-time notification attempt sent to teacher %d (question %d).",
                        teacherId, questionId);
            }
        } catch (Exception e) {
            // Notification failures must not prevent successful submission
            Log.error(AnswerService.class,
                    "Failed to notify teacher in real time: %s", e.getMessage());
        }

        return true;
    }

    /**
     * Returns all answers for a question (teacher view).
     * Calculates {@code isCorrect} in memory.
     *
     * @param dto query parameters
     * @return list of answers
     * @throws Exception if DB access fails
     */
    @Override
    // TODO Docente: consulta dos detalhes associados a uma pergunta expirada, incluindo as respostas
    public List<Answer> viewAnswers(ViewAnswersDTO dto) throws Exception {
        Integer questionId = dto.questionId();
        Integer teacherId = dto.teacherId();

        // check question ownership
        Map<String, Object> rec = dbCommands.selectOne(
                "SELECT correct_option FROM question WHERE id = ? AND teacher_id = ?",
                questionId, teacherId
        );
        if (rec == null) {
            throw new IllegalArgumentException("Pergunta não encontrada ou não pertence ao docente");
        }

        OptionLetter correct = OptionLetter.valueOf((String) rec.get("correct_option"));

        List<Answer> out = new ArrayList<>();
        try (var con = DriverManager.getConnection(dbCommands.getUrl());
             var ps = con.prepareStatement(
                     "SELECT a.student_id, a.chosen_option, a.created_at, " +
                             "s.name AS student_name, s.email AS student_email, s.student_number " +
                             "FROM answer a " +
                             "JOIN student s ON s.id = a.student_id " +
                             "WHERE a.question_id = ? " +
                             "ORDER BY a.created_at")) {
            ps.setInt(1, questionId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer stuId = rs.getInt("student_id");
                    OptionLetter sel = OptionLetter.valueOf(rs.getString("chosen_option"));
                    LocalDateTime at = LocalDateTime.parse(rs.getString("created_at"));
                    boolean isCorrect = sel.equals(correct);

                    String studentName = rs.getString("student_name");
                    String studentEmail = rs.getString("student_email");
                    Integer studentNumber = rs.getInt("student_number");

                    out.add(new Answer(
                            null,
                            stuId,
                            studentNumber,
                            questionId,
                            sel,
                            at,
                            isCorrect,
                            studentName,
                            studentEmail,
                            null
                    ));
                }
            }
        }
        return out;
    }

    /**
     * Returns the answer history of a student.
     * Calculates {@code isCorrect} using the question's {@code correct_option}.
     *
     * @param studentId student ID
     * @return list of answers for that student
     * @throws Exception if DB access fails
     */
    @Override
    public List<Answer> getStudentHistory(Integer studentId) throws Exception {
        List<Answer> out = new ArrayList<>();
        try (var con = DriverManager.getConnection(dbCommands.getUrl());
             var ps = con.prepareStatement(
                     "SELECT a.question_id, a.chosen_option, a.created_at, " +
                             "q.correct_option, q.statement " +
                             "FROM answer a " +
                             "JOIN question q ON q.id = a.question_id " +
                             "WHERE a.student_id = ? " +
                             "ORDER BY a.created_at DESC")) {
            ps.setInt(1, studentId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer qId = rs.getInt("question_id");
                    OptionLetter sel = OptionLetter.valueOf(rs.getString("chosen_option"));
                    LocalDateTime at = LocalDateTime.parse(rs.getString("created_at"));

                    String stmt = rs.getString("statement");
                    String corrStr = rs.getString("correct_option");
                    boolean isCorrect = false;
                    if (corrStr != null) {
                        OptionLetter corr = OptionLetter.valueOf(corrStr);
                        isCorrect = sel.equals(corr);
                    }

                    out.add(new Answer(
                            null,
                            studentId,
                            null, // studentNumber
                            qId,
                            sel,
                            at,
                            isCorrect,
                            null,  // studentName
                            null,  // studentEmail
                            stmt   // questionStatement
                    ));
                }
            }
            return out;
        }
    }
}
