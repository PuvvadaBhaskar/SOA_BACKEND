package com.archivalia.fine.entity;

public enum FineStatus {
    /** Returned on time - nothing to pay. */
    NO_FINE,
    /** Fine outstanding. */
    UNPAID,
    /** Fine settled. */
    PAID,
    /** Fine cancelled by an administrator. */
    WAIVED
}
