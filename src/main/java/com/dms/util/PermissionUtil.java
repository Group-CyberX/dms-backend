package com.dms.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

public class PermissionUtil {

    private static final Logger log = LoggerFactory.getLogger(PermissionUtil.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Map<String, Boolean> parsePermissions(String json) {
        try {
            if (json == null || json.isEmpty()) {
                return Collections.emptyMap();
            }

            return objectMapper.readValue(
                    json,
                    new TypeReference<Map<String, Boolean>>() {}
            );

        } catch (Exception e) {
            // Fail closed, but make it visible: an empty map revokes every permission.
            log.warn("Could not parse role permissions, treating as no permissions", e);
            return Collections.emptyMap();
        }
    }
}