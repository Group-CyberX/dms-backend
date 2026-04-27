package com.dms.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class CreateShareLinkRequest {
    private UUID documentId;
    private String accessLevel;
    private int expiryDays;
    private boolean requireAuth;
    private boolean allowDownload;
    private boolean allowComments;
    private String password;
}
