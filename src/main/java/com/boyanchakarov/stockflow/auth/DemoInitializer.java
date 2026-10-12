package com.boyanchakarov.stockflow.auth;

import com.boyanchakarov.stockflow.catalog.*;
import com.boyanchakarov.stockflow.customer.*;
import com.boyanchakarov.stockflow.inventory.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo & !prod")
public class DemoInitializer implements ApplicationRunner {
  private final AppUserRepository users;
  private final ProductRepository products;
  private final CustomerRepository customers;
  private final InventoryService inventory;
  private final PasswordEncoder encoder;
  private final Clock clock;
  private final JdbcTemplate jdbc;
  private final String adminPassword, staffPassword;

  public DemoInitializer(
      AppUserRepository users,
      ProductRepository products,
      CustomerRepository customers,
      InventoryService inventory,
      PasswordEncoder encoder,
      Clock clock,
      JdbcTemplate jdbc,
      @Value("${stockflow.demo.admin-password}") String adminPassword,
      @Value("${stockflow.demo.staff-password}") String staffPassword) {
    this.users = users;
    this.products = products;
    this.customers = customers;
    this.inventory = inventory;
    this.encoder = encoder;
    this.clock = clock;
    this.jdbc = jdbc;
    this.adminPassword = adminPassword;
    this.staffPassword = staffPassword;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (adminPassword.isBlank()
        || staffPassword.isBlank()
        || adminPassword.startsWith("replace-")
        || staffPassword.startsWith("replace-"))
      throw new IllegalStateException("Set distinct local demo account passwords before starting.");
    jdbc.execute("select pg_advisory_xact_lock(72651021)");
    account("admin", "ADMIN", adminPassword);
    account("staff", "STAFF", staffPassword);
    users.flush();
    var previous = SecurityContextHolder.getContext();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    SecurityContextHolder.setContext(context);
    try {
      String[] names = {
        "Blue ceramic mug",
        "Linen notebook",
        "Desk organiser",
        "Brass pen",
        "Cotton tote",
        "Glass bottle",
        "Oak tray",
        "Wool desk mat",
        "Travel tumbler",
        "Archive sample"
      };
      String[] skus = {
        "MUG-BLUE",
        "NOTE-LINEN",
        "DESK-ORG",
        "PEN-BRASS",
        "TOTE-COTTON",
        "BOTTLE-GLASS",
        "TRAY-OAK",
        "MAT-WOOL",
        "CUP-TRAVEL",
        "OLD-SAMPLE"
      };
      String[] prices = {
        "12.50", "8.90", "24.00", "16.75", "14.50", "19.90", "32.00", "45.00", "22.50", "5.00"
      };
      for (int i = 0; i < 10; i++) {
        UUID id = UUID.fromString("10000000-0000-4000-8000-" + String.format("%012d", i + 1));
        if (products.existsById(id)) continue;
        Product p =
            new Product(
                id,
                skus[i],
                names[i],
                "Synthetic demo catalogue item",
                new BigDecimal(prices[i]),
                5,
                clock.instant());
        if (i == 9)
          p.edit(p.getName(), p.getDescription(), p.getUnitPrice(), 5, false, clock.instant());
        products.saveAndFlush(p);
        if (i > 0)
          inventory.receive(
              new InventoryDtos.Receive(id, i == 3 ? 2 : 15 + i * 3, "Opening demo stock"),
              UUID.nameUUIDFromBytes(
                  ("stockflow-seed-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      }
      String[] namesC = {"Northline Studio", "Paper & Pine", "Atelier Forma"};
      for (int i = 0; i < 3; i++) {
        UUID id = UUID.fromString("20000000-0000-4000-8000-" + String.format("%012d", i + 1));
        if (!customers.existsById(id))
          customers.save(
              new Customer(
                  id,
                  namesC[i],
                  "orders" + (i + 1) + "@example.com",
                  null,
                  "Synthetic demo customer",
                  clock.instant()));
      }
    } finally {
      SecurityContextHolder.setContext(previous);
    }
  }

  private void account(String username, String role, String password) {
    if (users.findByUsername(username).isEmpty())
      users.save(
          new AppUser(
              UUID.randomUUID(), username, encoder.encode(password), role, clock.instant()));
  }
}
