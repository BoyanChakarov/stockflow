package com.boyanchakarov.stockflow.reporting;

import org.springframework.web.bind.annotation.*;

@RestController
public class DashboardController {
  private final DashboardService service;

  public DashboardController(DashboardService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/dashboard")
  public DashboardService.Dashboard get() {
    return service.get();
  }
}
