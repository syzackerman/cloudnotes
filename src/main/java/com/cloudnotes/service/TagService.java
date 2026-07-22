package com.cloudnotes.service;

import com.cloudnotes.domain.Tag;
import com.cloudnotes.domain.User;
import com.cloudnotes.dto.tag.CreateTagRequest;
import com.cloudnotes.dto.tag.TagResponse;
import com.cloudnotes.exception.DuplicateTagNameException;
import com.cloudnotes.exception.TagNotFoundException;
import com.cloudnotes.repository.TagRepository;
import com.cloudnotes.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {

    private final TagRepository tagRepository;
    private final UserRepository userRepository;

    public TagService(TagRepository tagRepository, UserRepository userRepository) {
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TagResponse create(UUID userId, CreateTagRequest request) {
        String normalizedName = normalizeName(request.name());
        if (tagRepository.existsByUserIdAndNormalizedName(userId, normalizedName)) {
            throw new DuplicateTagNameException();
        }

        User user = userRepository.getReferenceById(userId);
        Tag tag = Tag.builder()
                .user(user)
                .name(cleanDisplayName(request.name()))
                .normalizedName(normalizedName)
                .build();

        try {
            return TagResponse.from(tagRepository.save(tag));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateTagNameException();
        }
    }

    @Transactional(readOnly = true)
    public List<TagResponse> findAll(UUID userId) {
        return tagRepository.findAllByUserIdOrderByNameAsc(userId).stream()
                .map(TagResponse::from)
                .toList();
    }

    @Transactional
    public void delete(UUID userId, UUID tagId) {
        Tag tag = tagRepository.findByIdAndUserId(tagId, userId).orElseThrow(TagNotFoundException::new);
        tagRepository.deleteNoteRelationships(tag.getId());
        tagRepository.delete(tag);
    }

    public static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private String cleanDisplayName(String name) {
        return name.trim();
    }
}
