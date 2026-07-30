package com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out;

/**
 * Signals that the unique constraint on (user, Asset, transcript row) already holds. The database
 * owns that concurrency boundary, so the store reports the rejection instead of the application
 * reading first and branching in Java.
 */
public class SavedMomentAlreadySavedException extends RuntimeException {

    public SavedMomentAlreadySavedException(Throwable cause) {
        super("Saved moment already exists", cause);
    }
}
