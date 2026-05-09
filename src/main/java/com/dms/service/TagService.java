package com.dms.service;

import com.dms.dao.DocumentTagRepository;
import com.dms.dao.TagRepository;
import com.dms.models.DocumentTag;
import com.dms.models.Tag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TagService {

    private final TagRepository tagRepository;
    private final DocumentTagRepository documentTagRepository;

    public TagService(TagRepository tagRepository, DocumentTagRepository documentTagRepository) {
        this.tagRepository = tagRepository;
        this.documentTagRepository = documentTagRepository;
    }

    //Get all available tags (for dropdown/autocomplete)
    public List<Tag> getAllTags() {
        return tagRepository.findAll();
    }

    //Get or create a tag by name
     
    public Tag getOrCreateTag(String tagName) {
        Optional<Tag> existing = tagRepository.findByTagNameIgnoreCase(tagName.trim());
        if (existing.isPresent()) {
            return existing.get();
        }
        
        Tag newTag = new Tag(UUID.randomUUID(), tagName.trim());
        return tagRepository.save(newTag);
    }

    @Transactional
    public Tag addTagToDocument(UUID documentId, String tagName) {
        Tag tag = getOrCreateTag(tagName);

        if (!documentTagRepository.existsByDocumentIdAndTagId(documentId, tag.getTag_id())) {
            DocumentTag documentTag = new DocumentTag(UUID.randomUUID(), documentId, tag.getTag_id());
            documentTagRepository.save(documentTag);
        }

        return tag;
    }

    //Save tags for a document
     
    @Transactional
    public void saveTags(UUID documentId, String tagsString) {
        if (tagsString == null || tagsString.isBlank()) {
            return;
        }

        // Parse comma-separated tags
        String[] tagNames = tagsString.split(",");
        List<DocumentTag> documentTags = new ArrayList<>();

        for (String tagName : tagNames) {
            String trimmedTag = tagName.trim();
            if (!trimmedTag.isEmpty()) {
                Tag tag = getOrCreateTag(trimmedTag);

                if (!documentTagRepository.existsByDocumentIdAndTagId(documentId, tag.getTag_id())) {
                    documentTags.add(new DocumentTag(UUID.randomUUID(), documentId, tag.getTag_id()));
                }
            }
        }

        if (!documentTags.isEmpty()) {
            documentTagRepository.saveAll(documentTags);
        }
    }

    //Get all tags for a document
     
    public List<Tag> getTagsForDocument(UUID documentId) {
        List<DocumentTag> documentTags = documentTagRepository.findByDocumentId(documentId);
        List<Tag> tags = new ArrayList<>();
        
        for (DocumentTag docTag : documentTags) {
            Optional<Tag> tag = tagRepository.findById(docTag.getTagId());
            tag.ifPresent(tags::add);
        }
        
        return tags;
    }

    //Delete all tags for a document
     
    @Transactional
    public void deleteTagsForDocument(UUID documentId) {
        documentTagRepository.deleteByDocumentId(documentId);
    }
}
