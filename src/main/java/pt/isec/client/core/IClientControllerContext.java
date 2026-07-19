package pt.isec.client.core;

import pt.isec.client.services.AnswerClientService;
import pt.isec.client.services.AuthClientService;
import pt.isec.client.services.QuestionClientService;

import java.beans.PropertyChangeListener;

/**
 * Context interface exposed to UI controllers.
 * <p>
 * Provides:
 * <ul>
 *     <li>Property change subscription</li>
 *     <li>Authentication/user state accessors</li>
 *     <li>Access to client-side services</li>
 *     <li>High-level logout operation</li>
 * </ul>
 */
public interface IClientControllerContext {

    /* ======================= Property change listeners ======================= */

    /**
     * Adds a listener for a specific observable property.
     *
     * @param prop property name
     * @param l    listener instance
     */
    void addPropertyChangeListener(String prop, PropertyChangeListener l);

    /**
     * Removes a listener for a specific observable property.
     *
     * @param prop property name
     * @param l    listener to remove
     */
    void removePropertyChangeListener(String prop, PropertyChangeListener l);

    /* ======================= User / auth state (getters) ===================== */

    String getUserType();

    String getUserEmail();

    String getUserName();

    Integer getUserId();

    Long getStudentNumber();

    /* ======================= User / auth state (setters) ===================== */

    void setUserId(Integer id);

    void setStudentNumber(Long number);

    void setUserType(String t);

    void setUserEmail(String e);

    void setUserName(String n);

    void setAuthenticated(boolean authenticated);

    /* ======================= Service access ================================= */

    AuthClientService getAuthService();

    QuestionClientService getQuestionService();

    AnswerClientService getAnswerService();

    /* ======================= High-level operations =========================== */

    /**
     * Clears authentication state on the client side.
     * <p>
     * Does not send any network request (server logout must be handled
     * through {@link AuthClientService#logout()}).
     */
    void logout();
}