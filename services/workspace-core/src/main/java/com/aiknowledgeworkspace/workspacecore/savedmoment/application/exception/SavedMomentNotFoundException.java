package com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception;

/**
 * Raised for a missing, foreign or no-longer-canonical saved moment. All three collapse into the
 * same public response so the API never reveals whether the identifier exists.
 */
public class SavedMomentNotFoundException extends RuntimeException {

    public SavedMomentNotFoundException() {
        super("Saved moment not found");
    }
}
