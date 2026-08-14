package com.dms.rest;

import com.dms.dto.MoveDocumentsRequest;
import com.dms.service.FolderTreeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentMoveController {

    private final FolderTreeService folderTreeService;

    public DocumentMoveController(FolderTreeService folderTreeService) {
        this.folderTreeService = folderTreeService;
    }

    @PostMapping("/move")
    public ResponseEntity<Map<String, Object>> move(@RequestBody MoveDocumentsRequest request, HttpServletRequest httpReq) {
        int moved = folderTreeService.moveDocuments(request, httpReq.getRemoteAddr());
        Map<String, Object> resp = new HashMap<>();
        resp.put("movedCount", moved);
        return ResponseEntity.ok(resp);
    }
}
