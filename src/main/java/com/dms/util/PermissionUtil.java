package com.dms.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Map;

public class PermissionUtil {

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
            return Collections.emptyMap();
        }
    }
}