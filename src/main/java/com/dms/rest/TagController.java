package com.dms.rest;

import com.dms.dto.TagDTO;
import com.dms.models.Tag;
import com.dms.service.TagService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    //Get all tags for a document 
    @GetMapping("/document/{documentId}")
    public ResponseEntity<List<TagDTO>> getTagsForDocument(@PathVariable("documentId") UUID documentId) {
        List<Tag> tags = tagService.getTagsForDocument(documentId);
        List<TagDTO> tagDTOs = tags.stream()
                .map(t -> new TagDTO(t.getTag_id(), t.getTag_name()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(tagDTOs);
    }

    //Add a new tag to a document
    @Transactional
    @PostMapping("/document/{documentId}")
    public ResponseEntity<TagDTO> addTagToDocument(
            @PathVariable("documentId") UUID documentId,
            @RequestParam("tagName") String tagName) {
        Tag tag = tagService.addTagToDocument(documentId, tagName);
        return ResponseEntity.ok(new TagDTO(tag.getTag_id(), tag.getTag_name()));
    }
}
