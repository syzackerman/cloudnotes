package com.cloudnotes.controller;

import com.cloudnotes.config.OpenApiConfig;
import com.cloudnotes.dto.attachment.AttachmentResponse;
import com.cloudnotes.dto.attachment.DownloadUrlResponse;
import com.cloudnotes.security.AuthenticatedUser;
import com.cloudnotes.service.AttachmentService;
import com.cloudnotes.service.RateLimiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/notes/{noteId}/attachments")
@Tag(name = "Attachments", description = "Private S3-backed note attachments")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final RateLimiterService rateLimiterService;

    public AttachmentController(AttachmentService attachmentService, RateLimiterService rateLimiterService) {
        this.attachmentService = attachmentService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload an attachment")
    AttachmentResponse upload(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID noteId,
            @RequestParam("file") MultipartFile file) {
        rateLimiterService.checkUpload(authenticatedUser.id().toString());
        return attachmentService.upload(authenticatedUser.id(), noteId, file);
    }

    @GetMapping
    @Operation(summary = "List note attachments")
    List<AttachmentResponse> findAll(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable UUID noteId) {
        return attachmentService.findAll(authenticatedUser.id(), noteId);
    }

    @GetMapping("/{attachmentId}/download")
    @Operation(summary = "Create a short-lived presigned attachment download URL")
    DownloadUrlResponse createDownloadUrl(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID noteId,
            @PathVariable UUID attachmentId) {
        rateLimiterService.checkDownloadUrl(authenticatedUser.id().toString());
        return attachmentService.createDownloadUrl(authenticatedUser.id(), noteId, attachmentId);
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an attachment")
    void delete(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID noteId,
            @PathVariable UUID attachmentId) {
        attachmentService.delete(authenticatedUser.id(), noteId, attachmentId);
    }
}
