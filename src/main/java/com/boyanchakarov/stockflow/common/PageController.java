package com.boyanchakarov.stockflow.common;

import com.boyanchakarov.stockflow.auth.*;
import com.boyanchakarov.stockflow.catalog.*;
import com.boyanchakarov.stockflow.customer.*;
import com.boyanchakarov.stockflow.inventory.*;
import com.boyanchakarov.stockflow.order.*;
import com.boyanchakarov.stockflow.reporting.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PageController {
  private final ActorProvider actors;
  private final ProductService products;
  private final CustomerService customers;
  private final OrderService orders;
  private final OrderQueries orderQueries;
  private final InventoryService inventory;
  private final DashboardService dashboard;

  public PageController(
      ActorProvider actors,
      ProductService products,
      CustomerService customers,
      OrderService orders,
      OrderQueries orderQueries,
      InventoryService inventory,
      DashboardService dashboard) {
    this.actors = actors;
    this.products = products;
    this.customers = customers;
    this.orders = orders;
    this.orderQueries = orderQueries;
    this.inventory = inventory;
    this.dashboard = dashboard;
  }

  private void page(Model m, String title, String section) {
    m.addAttribute("title", title);
    m.addAttribute("section", section);
    m.addAttribute("actor", actors.current());
  }

  @GetMapping("/login")
  public String login() {
    return "auth/login";
  }

  @GetMapping("/")
  public String dashboard(Model m) {
    page(m, "Overview", "overview");
    m.addAttribute("data", dashboard.get());
    return "dashboard";
  }

  @GetMapping("/products")
  public String products(
      Model m,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort) {
    page(m, "Products", "products");
    m.addAttribute("data", products.list(q, active, page, size, sort));
    return "products/list";
  }

  @GetMapping("/products/new")
  public String newProduct(Model m) {
    actors.admin();
    page(m, "New product", "products");
    m.addAttribute("product", null);
    return "products/form";
  }

  @GetMapping("/products/{id}/edit")
  public String editProduct(@PathVariable UUID id, Model m) {
    actors.admin();
    page(m, "Edit product", "products");
    m.addAttribute("product", products.get(id));
    return "products/form";
  }

  @GetMapping("/products/{id}")
  public String product(@PathVariable UUID id, Model m) {
    page(m, "Product details", "products");
    m.addAttribute("product", products.get(id));
    return "products/detail";
  }

  @GetMapping("/customers")
  public String customers(
      Model m,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort) {
    page(m, "Customers", "customers");
    m.addAttribute("data", customers.list(q, active, page, size, sort));
    return "customers/list";
  }

  @GetMapping("/customers/new")
  public String newCustomer(Model m) {
    page(m, "New customer", "customers");
    m.addAttribute("customer", null);
    return "customers/form";
  }

  @GetMapping("/customers/{id}/edit")
  public String editCustomer(@PathVariable UUID id, Model m) {
    page(m, "Edit customer", "customers");
    m.addAttribute("customer", customers.get(id));
    return "customers/form";
  }

  @GetMapping("/orders")
  public String orders(
      Model m,
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort) {
    page(m, "Orders", "orders");
    m.addAttribute("data", orderQueries.list(status, customerId, from, to, page, size, sort));
    return "orders/list";
  }

  @GetMapping("/orders/new")
  public String newOrder(Model m) {
    page(m, "Create order", "orders");
    return "orders/form";
  }

  @GetMapping("/orders/{id}")
  public String order(@PathVariable UUID id, Model m) {
    page(m, "Order details", "orders");
    m.addAttribute("order", orders.get(id));
    m.addAttribute("events", orders.events(id));
    return "orders/detail";
  }

  @GetMapping("/inventory/movements")
  public String history(
      Model m,
      @RequestParam(required = false) UUID productId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    page(m, "Stock history", "inventory");
    m.addAttribute("data", inventory.list(productId, type, from, to, page, size));
    return "inventory/movements";
  }
}
