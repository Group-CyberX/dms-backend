package com.dms.rest;

import com.dms.dao.FolderRepository;
import com.dms.models.Folders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderRepository folderRepository;

    public FolderController(FolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    @GetMapping
    public List<Folders> getAll() {
        return folderRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Folders> getById(@PathVariable("id") UUID id) {
        return folderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Folders> create(@RequestBody Folders folder) {
        if (folder.getFolder_id() == null) {
            folder.setFolder_id(UUID.randomUUID());
        }
        Folders saved = folderRepository.save(folder);
        return ResponseEntity.created(URI.create("/api/folders/" + saved.getFolder_id())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Folders> update(@PathVariable("id") UUID id, @RequestBody Folders update) {
        Optional<Folders> existingOpt = folderRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Folders existing = existingOpt.get();
        existing.setName(update.getName());
        existing.setParent_folder_id(update.getParent_folder_id());
        existing.setPath(update.getPath());
        Folders saved = folderRepository.save(existing);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        if (!folderRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        folderRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
