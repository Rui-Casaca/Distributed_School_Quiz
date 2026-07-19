package pt.isec.client.ui;

/**
 * Represents a UI-related object that holds resources/listeners which must be
 * explicitly released when the view/controller is disposed.
 */
public interface IDisposableProp {

    /**
     * Releases any resources, listeners or bindings held by this object.
     * <p>
     * Intended to be called when the owning view or controller is being destroyed.
     */
    void dispose();
}
