/*
 * Copyright (c) 2026 Quenaire. All rights reserved.
 *
 * This file is proprietary and confidential. It is part of an unpublished work
 * owned by Quenaire and is protected by copyright and trade secret law.
 *
 * No licence, express or implied, is granted. No part of this file may be
 * copied, modified, distributed, disclosed, or used to create derivative works
 * without the prior written permission of Quenaire.
 *
 * Unauthorised copying or distribution of this file, via any medium, is
 * strictly prohibited.
 */
package com.quenaire.api.rest;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class HomeController {

  /**
   * Origin the Next.js frontend is served from.
   *
   * <p>The landing page rendered here is marketing copy; the product itself (login, dashboard)
   * lives on the frontend origin. Its calls-to-action have to cross origins, so they cannot be
   * plain root-relative links.
   */
  @Value("${app.frontend-url:http://localhost:3000}")
  private String frontendUrl;

  @GetMapping("/")
  public ModelAndView getLandingPage() {
    ModelAndView view = new ModelAndView("index");
    view.addObject("appUrl", frontendUrl);
    return view;
  }

  @RestController
  @RequestMapping("/api")
  public static class ApiStatusController {
    @GetMapping
    public ResponseEntity<Map<String, Object>> getApiStatus() {
      return ResponseEntity.ok(
          Map.of(
              "app", "Quenaire Backend API",
              "version", "0.0.1-SNAPSHOT",
              "status", "UP & RUNNING",
              "timestamp", Instant.now().toString(),
              "swaggerUi", "/api/swagger-ui.html",
              "actuatorHealth", "/api/actuator/health",
              "description", "Automated Compliance & Security Questionnaire AI Platform"));
    }
  }

  /**
   * Replaces Spring's Whitelabel error page.
   *
   * <p>This process is an API server that also happens to render one marketing page. A raw
   * Whitelabel page tells a caller nothing; a JSON body naming the status and pointing at the API
   * index and the frontend origin tells them whether they are simply on the wrong port.
   */
  @RestController
  public static class JsonErrorController implements ErrorController {

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @RequestMapping("/error")
    public ResponseEntity<Map<String, Object>> handleError(HttpServletRequest request) {
      Object rawStatus = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
      HttpStatus status =
          rawStatus instanceof Integer code
              ? HttpStatus.resolve(code) != null
                  ? HttpStatus.valueOf(code)
                  : HttpStatus.INTERNAL_SERVER_ERROR
              : HttpStatus.INTERNAL_SERVER_ERROR;

      Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("status", status.value());
      body.put("error", status.getReasonPhrase());
      body.put("path", path != null ? path.toString() : "");
      body.put("timestamp", Instant.now().toString());

      if (status == HttpStatus.NOT_FOUND) {
        body.put("message", "No such endpoint on the API server.");
        body.put("apiIndex", "/api");
        body.put("apiDocs", "/api/swagger-ui.html");
        // The overwhelmingly common cause: a browser following an app
        // link against the API port.
        body.put("hint", "Application pages are served by the frontend at " + frontendUrl);
      }

      return ResponseEntity.status(status).body(body);
    }
  }
}
