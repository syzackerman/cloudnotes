package com.cloudnotes.controller;

import com.cloudnotes.config.OpenApiConfig;
import com.cloudnotes.dto.tag.CreateTagRequest;
import com.cloudnotes.dto.tag.TagResponse;
import com.cloudnotes.security.AuthenticatedUser;
import com.cloudnotes.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tags")
@Tag(name = "Tags", description = "Per-user tag management")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a tag")
    TagResponse create(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateTagRequest request) {
        return tagService.create(authenticatedUser.id(), request);
    }

    @GetMapping
    @Operation(summary = "List owned tags")
    List<TagResponse> findAll(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return tagService.findAll(authenticatedUser.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a tag and remove it from notes")
    void delete(@AuthenticationPrincipal AuthenticatedUser authenticatedUser, @PathVariable UUID id) {
        tagService.delete(authenticatedUser.id(), id);
    }
}
