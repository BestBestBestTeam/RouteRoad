package com.routeroad.auth.infrastructure;

import com.routeroad.auth.api.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;

@Component
public class JsonSecurityErrors {
    private final JsonMapper json;
    public JsonSecurityErrors(JsonMapper json) { this.json = json; }
    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), ApiError.of(code, message));
    }
}
