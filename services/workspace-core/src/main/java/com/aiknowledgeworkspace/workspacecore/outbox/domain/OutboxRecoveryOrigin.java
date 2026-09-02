package com.aiknowledgeworkspace.workspacecore.outbox.domain;

/**
 * Which mechanism returned a claimed event to the relay queue. Both origins perform the same state
 * transition; the distinction is forensic, so an operator reading a row can tell a scheduled stale
 * recovery from one they ran by hand, and either from an ordinary publication failure — whose
 * category names the failure instead.
 */
public enum OutboxRecoveryOrigin {

    /** The recovery scheduler found the publication claim older than the stale threshold. */
    AUTOMATIC_STALE_PUBLISHING,

    /** An operator requeued this specific event with the manual recovery command. */
    OPERATOR_COMMAND
}
