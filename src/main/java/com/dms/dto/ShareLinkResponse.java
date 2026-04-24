package com.dms.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ShareLinkResponse {
    private String url;
    private LocalDateTime expiresAt;
    private String accessLevel;

    public ShareLinkResponse(String url, LocalDateTime expiresAt, String accessLevel) {
        this.url = url;
        this.expiresAt = expiresAt;
        this.accessLevel = accessLevel;
    }
}
