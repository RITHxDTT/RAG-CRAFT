package com.ragcraft.analytics.api;

import com.ragcraft.analytics.api.AnalyticsApi.EventRequest;
import com.ragcraft.analytics.api.AnalyticsApi.Overview;
import com.ragcraft.analytics.service.AnalyticsService;
import com.ragcraft.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AnalyticsController {

    private final AnalyticsService service;
    private final CurrentUser currentUser;

    public AnalyticsController(AnalyticsService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** The caller's own workspace analytics (same shape the frontend analytics service produces). */
    @GetMapping("/analytics")
    public Overview mine(@RequestParam(defaultValue = "14") int days, @RequestParam(required = false) String locale) {
        return service.overview(currentUser.require(), false, days, locale);
    }

    /** Platform-wide analytics for administrators. */
    @GetMapping("/admin/analytics")
    public Overview platform(@RequestParam(defaultValue = "14") int days, @RequestParam(required = false) String locale) {
        return service.overview(currentUser.requireAdmin(), true, days, locale);
    }

    /** conversation-service publishes one event per exchange. */
    @PostMapping("/internal/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void event(@RequestBody EventRequest request) {
        service.record(request);
    }
}
