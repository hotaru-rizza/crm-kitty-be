package com.inkflow.crm.module.analytics;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.analytics.dto.AppointmentAnalyticsDto;
import com.inkflow.crm.module.analytics.dto.ClientAnalyticsDto;
import com.inkflow.crm.module.analytics.dto.PnlDto;
import com.inkflow.crm.module.analytics.dto.ServicePopularityDto;
import com.inkflow.crm.module.analytics.dto.StaffPerformanceDto;
import com.inkflow.crm.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/appointments")
    @RequirePermission({"calendar.view_all", "calendar.view_own"})
    public ResponseEntity<ApiResponse<AppointmentAnalyticsDto>> getAppointmentAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "day") String groupBy) {
        AppointmentAnalyticsDto result = analyticsService.getAppointmentAnalytics(from, to, groupBy);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/staff")
    @RequirePermission({"calendar.view_all", "calendar.view_own"})
    public ResponseEntity<ApiResponse<List<StaffPerformanceDto>>> getStaffPerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        List<StaffPerformanceDto> result = analyticsService.getStaffPerformance(from, to);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/services")
    @RequirePermission({"calendar.view_all", "calendar.view_own"})
    public ResponseEntity<ApiResponse<List<ServicePopularityDto>>> getServicePopularity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        List<ServicePopularityDto> result = analyticsService.getServicePopularity(from, to);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/clients")
    @RequirePermission({"clients.view_all", "clients.view_own"})
    public ResponseEntity<ApiResponse<ClientAnalyticsDto>> getClientAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "month") String groupBy) {
        ClientAnalyticsDto result = analyticsService.getClientAnalytics(from, to, groupBy);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/pnl")
    @RequirePermission({"finance.view"})
    public ResponseEntity<ApiResponse<PnlDto>> getPnl(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        PnlDto result = analyticsService.getPnl(from, to);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
