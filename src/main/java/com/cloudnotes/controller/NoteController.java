package com.cloudnotes.controller;

import com.cloudnotes.config.OpenApiConfig;
import com.cloudnotes.dto.note.CreateNoteRequest;
import com.cloudnotes.dto.note.FavoriteNoteRequest;
import com.cloudnotes.dto.note.NoteResponse;
import com.cloudnotes.dto.note.UpdateNoteRequest;
import com.cloudnotes.dto.note.UpdateNoteTagsRequest;
import com.cloudnotes.security.AuthenticatedUser;
import com.cloudnotes.service.NoteService;
import com.cloudnotes.service.PageableValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notes")
@Tag(name = "Notes", description = "Ownership-protected notes, search, favorites, tags, and trash")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NoteController {

    private final NoteService noteService;
    private final PageableValidator pageableValidator;

    public NoteController(NoteService noteService, PageableValidator pageableValidator) {
        this.noteService = noteService;
        this.pageableValidator = pageableValidator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a note")
    NoteResponse create(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateNoteRequest request) {
        return noteService.create(authenticatedUser.id(), request);
    }

    @GetMapping
    @Operation(
            summary = "List and search active notes",
            description =
                    "Supports case-insensitive search with q, favorite filtering, tag filtering, pagination, and safe sorting by updatedAt, createdAt, or title.")
    Page<NoteResponse> findAll(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) String tag,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        pageableValidator.validate(pageable);
        return noteService.findAll(authenticatedUser.id(), q, favorite, tag, pageable);
    }

    @GetMapping("/trash")
    @Operation(summary = "List soft-deleted notes")
    Page<NoteResponse> findTrash(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        pageableValidator.validate(pageable);
        return noteService.findTrash(authenticatedUser.id(), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one owned active note")
    NoteResponse findById(@AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable UUID id) {
        return noteService.findById(authenticatedUser.id(), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update note title and content")
    NoteResponse update(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateNoteRequest request) {
        return noteService.update(authenticatedUser.id(), id, request);
    }

    @PatchMapping("/{id}/favorite")
    @Operation(summary = "Set favorite status")
    NoteResponse updateFavorite(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID id,
            @Valid @RequestBody FavoriteNoteRequest request) {
        return noteService.updateFavorite(authenticatedUser.id(), id, request);
    }

    @PutMapping("/{id}/tags")
    @Operation(summary = "Replace all tags assigned to a note")
    NoteResponse replaceTags(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateNoteTagsRequest request) {
        return noteService.replaceTags(authenticatedUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a note")
    void delete(@AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable UUID id) {
        noteService.softDelete(authenticatedUser.id(), id);
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore a soft-deleted note")
    NoteResponse restore(@AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable UUID id) {
        return noteService.restore(authenticatedUser.id(), id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Permanently delete a soft-deleted note")
    void permanentDelete(@AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable UUID id) {
        noteService.permanentDelete(authenticatedUser.id(), id);
    }
}
