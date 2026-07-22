package com.cloudnotes.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ApplicationMetrics {

    private final Counter authSuccess;
    private final Counter authFailure;
    private final Counter notesCreated;
    private final Counter notesDeleted;
    private final Counter notesRestored;
    private final Counter attachmentsUploaded;
    private final Counter attachmentUploadFailures;

    public ApplicationMetrics(MeterRegistry meterRegistry) {
        this.authSuccess = meterRegistry.counter("cloudnotes.auth.success");
        this.authFailure = meterRegistry.counter("cloudnotes.auth.failure");
        this.notesCreated = meterRegistry.counter("cloudnotes.notes.created");
        this.notesDeleted = meterRegistry.counter("cloudnotes.notes.deleted");
        this.notesRestored = meterRegistry.counter("cloudnotes.notes.restored");
        this.attachmentsUploaded = meterRegistry.counter("cloudnotes.attachments.uploaded");
        this.attachmentUploadFailures = meterRegistry.counter("cloudnotes.attachments.upload.failures");
    }

    public void authSuccess() {
        authSuccess.increment();
    }

    public void authFailure() {
        authFailure.increment();
    }

    public void noteCreated() {
        notesCreated.increment();
    }

    public void noteDeleted() {
        notesDeleted.increment();
    }

    public void noteRestored() {
        notesRestored.increment();
    }

    public void attachmentUploaded() {
        attachmentsUploaded.increment();
    }

    public void attachmentUploadFailure() {
        attachmentUploadFailures.increment();
    }
}
