package com.boyanchakarov.stockflow;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.boyanchakarov.stockflow.auth.*;
import com.boyanchakarov.stockflow.catalog.*;
import com.boyanchakarov.stockflow.common.*;
import com.boyanchakarov.stockflow.customer.*;
import com.boyanchakarov.stockflow.inventory.*;
import com.boyanchakarov.stockflow.order.*;
import com.boyanchakarov.stockflow.reporting.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
@Import(StockFlowIT.Hooks.class)
class StockFlowIT extends DatabaseIT {
  @TestConfiguration
  static class Hooks {
    @Bean
    AtomicBoolean failBeforeCommit() {
      return new AtomicBoolean();
    }

    @Bean
    BeforeCommitHook beforeCommitHook(AtomicBoolean failure) {
      return op -> {
        if (failure.getAndSet(false))
          throw new IllegalStateException("Injected rollback test failure");
      };
    }
  }

  @Autowired ProductService products;
  @Autowired CustomerService customers;
  @Autowired InventoryService inventory;
  @Autowired OrderService orders;
  @Autowired OrderQueries queries;
  @Autowired DashboardService dashboard;
  @Autowired AppUserRepository users;
  @Autowired PasswordEncoder passwords;
  @Autowired JsonMapper json;
  @Autowired MockMvc mvc;
  @Autowired AtomicBoolean failBeforeCommit;
  @Autowired PlatformTransactionManager manager;
  UUID customer, customer2, product, product2;

  @BeforeEach
  void fixture() {
    jdbc.execute(
        "truncate"
            + " idempotency_records,order_events,stock_movements,order_items,orders,products,customers,app_users");
    users.save(
        new AppUser(
            UUID.randomUUID(),
            "admin",
            passwords.encode("Test-admin-password"),
            "ADMIN",
            Instant.now()));
    users.save(
        new AppUser(
            UUID.randomUUID(),
            "staff",
            passwords.encode("Test-staff-password"),
            "STAFF",
            Instant.now()));
    as("admin");
    failBeforeCommit.set(false);
    customer =
        customers
            .create(
                new CustomerDtos.Create("Original customer", "original@example.com", "123", null))
            .id();
    customer2 = customers.create(new CustomerDtos.Create("Second customer", null, null, null)).id();
    product =
        products
            .create(new ProductDtos.Create("MUG", "Mug", null, new BigDecimal("12.50"), 2L))
            .id();
    product2 =
        products
            .create(new ProductDtos.Create("PEN", "Pen", null, new BigDecimal("0.10"), 1L))
            .id();
  }

  @AfterEach
  void reconcile() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from products p where p.on_hand<>coalesce((select"
                    + " sum(delta_on_hand) from stock_movements m where m.product_id=p.id),0) or"
                    + " p.reserved<>coalesce((select sum(delta_reserved) from stock_movements m"
                    + " where m.product_id=p.id),0) or p.reserved<>coalesce((select sum(i.quantity)"
                    + " from order_items i join orders o on o.id=i.order_id where i.product_id=p.id"
                    + " and o.status='CONFIRMED'),0)",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from orders o where total<>(select sum(line_total) from"
                    + " order_items i where i.order_id=o.id)",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from idempotency_records where completed_at is null",
                Integer.class))
        .isZero();
    failBeforeCommit.set(false);
    SecurityContextHolder.clearContext();
  }

  void as(String username) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                username,
                null,
                List.of(
                    new SimpleGrantedAuthority(
                        "ROLE_" + (username.equals("admin") ? "ADMIN" : "STAFF")))));
  }

  void stock(UUID p, int q) {
    inventory.receive(new InventoryDtos.Receive(p, q, "Opening stock"), UUID.randomUUID());
  }

  OrderDtos.Create request(int quantity) {
    return new OrderDtos.Create(customer, List.of(new OrderDtos.Item(product, quantity)), null);
  }

  OrderDtos.Detail create(int quantity) {
    return json.readValue(
        orders.create(request(quantity), UUID.randomUUID()).body(), OrderDtos.Detail.class);
  }

  long count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Long.class);
  }

  void conflict(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  <T> List<Object> race(Supplier<T> first, Supplier<T> second) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
    try {
      List<Future<Object>> futures = new ArrayList<>();
      for (var action : List.of(first, second))
        futures.add(
            pool.submit(
                () -> {
                  as("admin");
                  ready.countDown();
                  if (!start.await(5, TimeUnit.SECONDS))
                    throw new IllegalStateException("Start timeout");
                  try {
                    return action.get();
                  } catch (BusinessException e) {
                    return e.code();
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return List.of(
          futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
    } finally {
      start.countDown();
      pool.shutdownNow();
      assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void exactTotalsSnapshotsAndAtomicCreation() {
    stock(product, 10);
    stock(product2, 10);
    var req =
        new OrderDtos.Create(
            customer,
            List.of(new OrderDtos.Item(product, 3), new OrderDtos.Item(product2, 3)),
            "Pickup");
    var result =
        json.readValue(orders.create(req, UUID.randomUUID()).body(), OrderDtos.Detail.class);
    assertThat(result.total()).isEqualTo("37.80");
    assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(products.get(product).reserved()).isEqualTo(3);
    assertThat(count("order_items")).isEqualTo(2);
    assertThat(count("order_events")).isEqualTo(1);
    assertThat(count("stock_movements")).isEqualTo(4);
  }

  @Test
  void insufficientLineRollsBackWholeOrderAndKey() {
    stock(product, 10);
    long keys = count("idempotency_records");
    var req =
        new OrderDtos.Create(
            customer,
            List.of(new OrderDtos.Item(product, 3), new OrderDtos.Item(product2, 1)),
            null);
    conflict(() -> orders.create(req, UUID.randomUUID()), "INSUFFICIENT_STOCK");
    assertThat(count("orders")).isZero();
    assertThat(count("order_items")).isZero();
    assertThat(count("order_events")).isZero();
    assertThat(count("stock_movements")).isEqualTo(1);
    assertThat(count("idempotency_records")).isEqualTo(keys);
    assertThat(products.get(product).reserved()).isZero();
  }

  @Test
  void allShortagesAreReported() {
    try {
      orders.create(
          new OrderDtos.Create(
              customer,
              List.of(new OrderDtos.Item(product, 2), new OrderDtos.Item(product2, 1)),
              null),
          UUID.randomUUID());
      fail("Expected shortage");
    } catch (BusinessException e) {
      assertThat((List<?>) e.properties().get("shortages")).hasSize(2);
    }
  }

  @Test
  void archivedRecordsRejectNewOrdersButExistingCanFulfil() {
    stock(product, 10);
    var order = create(3);
    products.update(product, json.readTree("{\"active\":false}"));
    conflict(() -> orders.create(request(1), UUID.randomUUID()), "PRODUCT_INACTIVE");
    assertThat(orders.fulfil(order.id()).status()).isEqualTo(OrderStatus.FULFILLED);
    customers.update(customer, json.readTree("{\"active\":false}"));
    conflict(() -> orders.create(request(1), UUID.randomUUID()), "CUSTOMER_INACTIVE");
  }

  @Test
  void laterEditsDoNotChangeSnapshots() {
    stock(product, 10);
    var order = create(3);
    products.update(product, json.readTree("{\"name\":\"New name\",\"unitPrice\":\"99.99\"}"));
    customers.update(customer, json.readTree("{\"name\":\"Renamed customer\",\"email\":null}"));
    assertThat(orders.get(order.id())).isEqualTo(order);
  }

  @Test
  void correctionsRespectReservationsAndLedger() {
    stock(product, 10);
    create(8);
    conflict(
        () ->
            inventory.adjust(
                new InventoryDtos.Adjust(product, -3, "Count correction"), UUID.randomUUID()),
        "INSUFFICIENT_STOCK");
    inventory.adjust(new InventoryDtos.Adjust(product, -2, "Count correction"), UUID.randomUUID());
    assertThat(products.get(product).onHand()).isEqualTo(8);
    assertThat(products.get(product).available()).isZero();
  }

  @Test
  void fulfilIsSafeToRepeatAndCancelConflicts() {
    stock(product, 10);
    var o = create(3);
    orders.fulfil(o.id());
    orders.fulfil(o.id());
    assertThat(products.get(product).onHand()).isEqualTo(7);
    assertThat(products.get(product).reserved()).isZero();
    assertThat(orders.events(o.id())).hasSize(2);
    assertThat(count("stock_movements")).isEqualTo(3);
    conflict(
        () -> orders.cancel(o.id(), new OrderDtos.Cancel("Too late")), "INVALID_ORDER_TRANSITION");
  }

  @Test
  void cancelRepeatsPreserveOriginalReason() {
    stock(product, 10);
    var o = create(3);
    orders.cancel(o.id(), new OrderDtos.Cancel("Original reason"));
    var current = orders.cancel(o.id(), new OrderDtos.Cancel("Second reason"));
    assertThat(current.cancellationReason()).isEqualTo("Original reason");
    assertThat(products.get(product).onHand()).isEqualTo(10);
    assertThat(products.get(product).reserved()).isZero();
    assertThat(orders.events(o.id())).hasSize(2);
    conflict(() -> orders.fulfil(o.id()), "INVALID_ORDER_TRANSITION");
  }

  @Test
  void lastUnitRaceWithDistinctCustomers() throws Exception {
    stock(product, 1);
    var r =
        race(
            () -> orders.create(request(1), UUID.randomUUID()),
            () ->
                orders.create(
                    new OrderDtos.Create(customer2, List.of(new OrderDtos.Item(product, 1)), null),
                    UUID.randomUUID()));
    assertThat(r.stream().filter(StoredResponse.class::isInstance)).hasSize(1);
    assertThat(r).contains("INSUFFICIENT_STOCK");
    assertThat(count("orders")).isEqualTo(1);
    assertThat(products.get(product).reserved()).isEqualTo(1);
  }

  @Test
  void reverseInputOrderCannotDeadlock() throws Exception {
    stock(product, 2);
    stock(product2, 2);
    var a =
        new OrderDtos.Create(
            customer,
            List.of(new OrderDtos.Item(product, 1), new OrderDtos.Item(product2, 1)),
            null);
    var b =
        new OrderDtos.Create(
            customer2,
            List.of(new OrderDtos.Item(product2, 1), new OrderDtos.Item(product, 1)),
            null);
    assertThat(
            race(
                () -> orders.create(a, UUID.randomUUID()),
                () -> orders.create(b, UUID.randomUUID())))
        .allMatch(StoredResponse.class::isInstance);
    assertThat(products.get(product).reserved()).isEqualTo(2);
    assertThat(products.get(product2).reserved()).isEqualTo(2);
  }

  @Test
  void fulfilCancelRaceHasOneTerminalTransition() throws Exception {
    stock(product, 10);
    var o = create(3);
    var results =
        race(
            () -> orders.fulfil(o.id()),
            () -> orders.cancel(o.id(), new OrderDtos.Cancel("Cancelled")));
    assertThat(results.stream().filter(OrderDtos.Detail.class::isInstance)).hasSize(1);
    assertThat(results).contains("INVALID_ORDER_TRANSITION");
    assertThat(orders.events(o.id())).hasSize(2);
    assertThat(products.get(product).reserved()).isZero();
    assertThat(products.get(product).onHand()).isIn(7L, 10L);
  }

  @Test
  void adjustmentCreateRacePreservesStock() throws Exception {
    stock(product, 1);
    var results =
        race(
            () ->
                inventory.adjust(
                    new InventoryDtos.Adjust(product, -1, "Count correction"), UUID.randomUUID()),
            () -> orders.create(request(1), UUID.randomUUID()));
    assertThat(results.stream().filter(StoredResponse.class::isInstance)).hasSize(1);
    assertThat(results).contains("INSUFFICIENT_STOCK");
  }

  @Test
  void concurrentDuplicateOrderCommitsOnlyOnce() throws Exception {
    stock(product, 10);
    UUID key = UUID.randomUUID();
    var results = race(() -> orders.create(request(3), key), () -> orders.create(request(3), key));
    var a = (StoredResponse) results.get(0);
    var b = (StoredResponse) results.get(1);
    assertThat(json.readTree(a.body())).isEqualTo(json.readTree(b.body()));
    assertThat(a.status()).isEqualTo(b.status());
    assertThat(a.location()).isEqualTo(b.location());
    assertThat(count("orders")).isEqualTo(1);
    assertThat(products.get(product).reserved()).isEqualTo(3);
  }

  @Test
  void receiptsAndAdjustmentsReplaySequentiallyAndConcurrently() throws Exception {
    UUID receipt = UUID.randomUUID();
    var req = new InventoryDtos.Receive(product, 10, "Opening");
    var rr = race(() -> inventory.receive(req, receipt), () -> inventory.receive(req, receipt));
    assertThat(json.readTree(((StoredResponse) rr.get(0)).body()))
        .isEqualTo(json.readTree(((StoredResponse) rr.get(1)).body()));
    assertThat(products.get(product).onHand()).isEqualTo(10);
    UUID adjust = UUID.randomUUID();
    var a = new InventoryDtos.Adjust(product, -2, "Count");
    var first = inventory.adjust(a, adjust);
    var second = inventory.adjust(a, adjust);
    assertThat(json.readTree(first.body())).isEqualTo(json.readTree(second.body()));
    assertThat(products.get(product).onHand()).isEqualTo(8);
    assertThat(count("stock_movements")).isEqualTo(2);
  }

  @Test
  void changedPayloadKeyConflictsAndActorScopeIsIndependent() {
    stock(product, 20);
    UUID key = UUID.randomUUID();
    orders.create(request(3), key);
    conflict(() -> orders.create(request(4), key), "IDEMPOTENCY_KEY_REUSED");
    as("staff");
    orders.create(request(3), key);
    assertThat(count("orders")).isEqualTo(2);
  }

  @Test
  void normalizedLineOrderAndWhitespaceReplay() {
    stock(product, 10);
    stock(product2, 10);
    UUID key = UUID.randomUUID();
    var a =
        new OrderDtos.Create(
            customer,
            List.of(new OrderDtos.Item(product, 1), new OrderDtos.Item(product2, 2)),
            " Pickup ");
    var b =
        new OrderDtos.Create(
            customer,
            List.of(new OrderDtos.Item(product2, 2), new OrderDtos.Item(product, 1)),
            "Pickup");
    assertThat(json.readTree(orders.create(a, key).body()))
        .isEqualTo(json.readTree(orders.create(b, key).body()));
    assertThat(count("orders")).isEqualTo(1);
  }

  @Test
  void replayAfterFulfilAndArchiveReturnsOriginalConfirmedSnapshot() {
    stock(product, 10);
    UUID key = UUID.randomUUID();
    var first = orders.create(request(3), key);
    UUID id = json.readValue(first.body(), OrderDtos.Detail.class).id();
    orders.fulfil(id);
    products.update(product, json.readTree("{\"active\":false}"));
    customers.update(customer, json.readTree("{\"active\":false}"));
    var replay = orders.create(request(3), key);
    assertThat(json.readTree(replay.body())).isEqualTo(json.readTree(first.body()));
    assertThat(json.readTree(replay.body()).get("status").asString()).isEqualTo("CONFIRMED");
    assertThat(orders.get(id).status()).isEqualTo(OrderStatus.FULFILLED);
  }

  @Test
  void thrownAfterFlushRollsBackAllWritesIncludingClaim() {
    stock(product, 10);
    long keys = count("idempotency_records");
    failBeforeCommit.set(true);
    assertThatThrownBy(() -> orders.create(request(3), UUID.randomUUID()))
        .hasMessageContaining("Injected");
    assertThat(count("orders")).isZero();
    assertThat(count("order_items")).isZero();
    assertThat(count("order_events")).isZero();
    assertThat(count("stock_movements")).isEqualTo(1);
    assertThat(count("idempotency_records")).isEqualTo(keys);
    assertThat(products.get(product).reserved()).isZero();
  }

  @Test
  void failedBusinessRequestCanRetrySameKey() {
    UUID key = UUID.randomUUID();
    conflict(() -> orders.create(request(3), key), "INSUFFICIENT_STOCK");
    stock(product, 10);
    assertThat(orders.create(request(3), key).status()).isEqualTo(201);
  }

  @Test
  void presenceAwarePatchAndImmutableFields() throws Exception {
    customers.update(customer, json.readTree("{\"notes\":\"Updated\"}"));
    assertThat(customers.get(customer).email()).isEqualTo("original@example.com");
    customers.update(customer, json.readTree("{\"email\":null}"));
    assertThat(customers.get(customer).email()).isNull();
    for (String body :
        List.of(
            "{}",
            "{\"name\":null}",
            "{\"sku\":\"CHANGED\"}",
            "{\"onHand\":4}",
            "{\"unitPrice\":\"1.234\"}"))
      mvc.perform(
              patch("/api/v1/products/" + product)
                  .with(user("admin").roles("ADMIN"))
                  .with(csrf())
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isBadRequest());
  }

  @Test
  void unknownFieldsAndInvalidQuantitiesAreRejected() throws Exception {
    stock(product, 10);
    String valid = json.writeValueAsString(request(1));
    mvc.perform(
            post("/api/v1/orders")
                .with(user("staff").roles("STAFF"))
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json")
                .content(valid.substring(0, valid.length() - 1) + ",\"total\":\"0.01\"}"))
        .andExpect(status().isBadRequest());
    for (int q : List.of(0, -1, 1000001)) {
      mvc.perform(
              post("/api/v1/orders")
                  .with(user("staff").roles("STAFF"))
                  .with(csrf())
                  .header("Idempotency-Key", UUID.randomUUID())
                  .contentType("application/json")
                  .content(json.writeValueAsString(request(q))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fieldErrors").isArray());
    }
    assertThat(count("orders")).isZero();
  }

  @Test
  void duplicateAndOversizedOrdersAreRejected() {
    var dup =
        new OrderDtos.Create(
            customer,
            List.of(new OrderDtos.Item(product, 1), new OrderDtos.Item(product, 2)),
            null);
    conflict(() -> orders.create(dup, UUID.randomUUID()), "VALIDATION_FAILED");
    var tooMany =
        new OrderDtos.Create(
            customer,
            java.util.stream.IntStream.range(0, 51)
                .mapToObj(i -> new OrderDtos.Item(UUID.randomUUID(), 1))
                .toList(),
            null);
    assertThatThrownBy(() -> orders.create(tooMany, UUID.randomUUID()))
        .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    assertThat(count("orders")).isZero();
  }

  @Test
  void rolesAndCsrfAreEnforcedInHttpAndServices() throws Exception {
    SecurityContextHolder.clearContext();
    mvc.perform(get("/api/v1/products"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mvc.perform(get("/products")).andExpect(status().is3xxRedirection());
    mvc.perform(
            post("/api/v1/customers")
                .with(user("staff").roles("STAFF"))
                .contentType("application/json")
                .content("{\"name\":\"Test\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.requestId").isNotEmpty());
    for (String path :
        List.of("/api/v1/products", "/api/v1/inventory/receipts", "/api/v1/inventory/adjustments"))
      mvc.perform(
              post(path)
                  .with(user("staff").roles("STAFF"))
                  .with(csrf())
                  .contentType("application/json")
                  .content("{}"))
          .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/inventory/movements").with(user("staff").roles("STAFF")))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/v1/customers/" + customer)
                .with(user("staff").roles("STAFF"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"active\":false}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/v1/customers/" + customer)
                .with(user("staff").roles("STAFF"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"name\":\"Allowed edit\"}"))
        .andExpect(status().isOk());
    as("staff");
    conflict(
        () ->
            inventory.receive(
                new InventoryDtos.Receive(product, 1, "Forbidden"), UUID.randomUUID()),
        "FORBIDDEN");
    conflict(() -> products.update(product, json.readTree("{\"active\":false}")), "FORBIDDEN");
  }

  @Test
  void realFormLoginRenewsCsrfAndLogoutRequiresToken() throws Exception {
    SecurityContextHolder.clearContext();
    var before = mvc.perform(get("/api/v1/csrf")).andExpect(status().isOk()).andReturn();
    var session = (MockHttpSession) before.getRequest().getSession(false);
    var token = json.readTree(before.getResponse().getContentAsString());
    mvc.perform(
            post("/login")
                .session(session)
                .param("username", "admin")
                .param("password", "Test-admin-password"))
        .andExpect(status().isForbidden());
    var login =
        mvc.perform(
                post("/login")
                    .session(session)
                    .param("username", " ADMIN ")
                    .param("password", "Test-admin-password")
                    .param("_csrf", token.get("token").asString()))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    session = (MockHttpSession) login.getRequest().getSession(false);
    mvc.perform(get("/api/v1/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("admin"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    var after = mvc.perform(get("/api/v1/csrf").session(session)).andReturn();
    assertThat(json.readTree(after.getResponse().getContentAsString()).get("token").asString())
        .isNotEqualTo(token.get("token").asString());
    mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
    mvc.perform(post("/logout").session(session).with(csrf()))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void paginationFiltersDatesAndDashboardAreAccurate() throws Exception {
    stock(product, 10);
    create(1);
    var page = products.list(null, true, 0, 1, "sku");
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.content().getFirst().sku()).isEqualTo("MUG");
    assertThat(products.list(null, true, 1, 1, "sku").content().getFirst().sku()).isEqualTo("PEN");
    assertThat(products.list("mug", true, 0, 20, null).content()).hasSize(1);
    assertThat(
            queries
                .list(
                    OrderStatus.CONFIRMED,
                    customer,
                    Instant.parse("2000-01-01T00:00:00Z"),
                    Instant.parse("2100-01-01T00:00:00Z"),
                    0,
                    20,
                    null)
                .content()
                .getFirst()
                .quantity())
        .isEqualTo(1);
    var d = dashboard.get();
    assertThat(d.activeProductCount()).isEqualTo(2);
    assertThat(d.confirmedOrderCount()).isEqualTo(1);
    assertThat(d.lowStockProductCount()).isEqualTo(1);
    mvc.perform(get("/api/v1/products?sort=onHand;drop&page=0").with(user("staff").roles("STAFF")))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/products?size=101").with(user("staff").roles("STAFF")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void duplicateSkuAndMissingRecordsHaveSpecificErrors() throws Exception {
    mvc.perform(
            post("/api/v1/products")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"sku\":\" mug"
                        + " \",\"name\":\"Other\",\"unitPrice\":\"1.00\",\"reorderThreshold\":0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
    mvc.perform(get("/api/v1/products/" + UUID.randomUUID()).with(user("staff").roles("STAFF")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void stockLockTimeoutIsRetryableAndLeavesNoClaim() throws Exception {
    stock(product, 10);
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    long keys = count("idempotency_records");
    Future<?> holder =
        pool.submit(
            () ->
                new TransactionTemplate(manager)
                    .execute(
                        s -> {
                          jdbc.queryForObject(
                              "select id from products where id=? for update", UUID.class, product);
                          locked.countDown();
                          try {
                            if (!release.await(12, TimeUnit.SECONDS))
                              throw new IllegalStateException("Release timeout");
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                          }
                          return null;
                        }));
    try {
      assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
      mvc.perform(
              post("/api/v1/orders")
                  .with(user("staff").roles("STAFF"))
                  .with(csrf())
                  .header("Idempotency-Key", UUID.randomUUID())
                  .contentType("application/json")
                  .content(json.writeValueAsString(request(1))))
          .andExpect(status().isServiceUnavailable())
          .andExpect(header().string("Retry-After", "1"))
          .andExpect(jsonPath("$.code").value("RETRYABLE_CONFLICT"));
      assertThat(count("orders")).isZero();
      assertThat(count("idempotency_records")).isEqualTo(keys);
    } finally {
      release.countDown();
      holder.get(5, TimeUnit.SECONDS);
      pool.shutdownNow();
    }
  }
}
