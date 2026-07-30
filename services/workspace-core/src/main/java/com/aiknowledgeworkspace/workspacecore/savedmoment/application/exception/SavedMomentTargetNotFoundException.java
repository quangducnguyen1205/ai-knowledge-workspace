package com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception;

/**
 * Raised when the requested Asset is missing or foreign, or the canonical transcript row does not
 * currently exist. All of those collapse into one public response so the API never reveals which
 * identifiers exist.
 */
public class SavedMomentTargetNotFoundException extends RuntimeException {

    public SavedMomentTargetNotFoundException() {
        super("Video moment not found");
    }
}
