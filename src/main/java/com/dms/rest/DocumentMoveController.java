package com.dms.rest;

import com.dms.dto.MoveDocumentsRequest;
import com.dms.security.SecurityUtils;
import com.dms.service.FolderTreeService;
import com.dms.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentMoveController {

    private final FolderTreeService folderTreeService;
    private final PermissionService permissionService;

    public DocumentMoveController(FolderTreeService folderTreeService, PermissionService permissionService) {
        this.folderTreeService = folderTreeService;
        this.permissionService = permissionService;
    }

    @PostMapping("/move")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canEditDocument')")
    public ResponseEntity<Map<String, Object>> move(@RequestBody MoveDocumentsRequest request,
                                                    HttpServletRequest httpReq,
                                                    Authentication auth) {
        UUID ownerId = permissionService.hasPermission(auth, "canManageAllDocuments")
                ? null
                : SecurityUtils.currentUserId();
        int moved = folderTreeService.moveDocuments(request, httpReq.getRemoteAddr(), ownerId);
        Map<String, Object> resp = new HashMap<>();
        resp.put("movedCount", moved);
        return ResponseEntity.ok(resp);
    }
}
