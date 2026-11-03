package com.archivalia.borrow.dto;

import com.archivalia.borrow.entity.Borrow;

/**
 * Result of returning a book: the updated borrow, the fine details (null if fine-service could not
 * be reached) and a warning explaining a deferred fine calculation.
 */
public record ReturnResponse(Borrow borrow, FineResponse fine, String warning) {
}
