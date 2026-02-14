package org.sirohi.smartnotebook.domain;

/**
 * Status lifecycle for uploaded documents.
 * PENDING → PROCESSING → READY | FAILED
 */
public enum DocumentStatus {
    PENDING,
    PROCESSING,
    READY,
    FAILED
}
