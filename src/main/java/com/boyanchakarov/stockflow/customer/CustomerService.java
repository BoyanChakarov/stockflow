package com.boyanchakarov.stockflow.customer;

import com.boyanchakarov.stockflow.auth.ActorProvider;
import com.boyanchakarov.stockflow.common.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class CustomerService {
  private final CustomerRepository customers;
  private final ActorProvider actors;
  private final WriteTransactions tx;
  private final Clock clock;
  private final Validation validation;

  public CustomerService(
      CustomerRepository customers,
      ActorProvider actors,
      WriteTransactions tx,
      Clock clock,
      Validation validation) {
    this.customers = customers;
    this.actors = actors;
    this.tx = tx;
    this.clock = clock;
    this.validation = validation;
  }

  @Transactional(readOnly = true)
  public PageResponse<CustomerDtos.View> list(
      String q, Boolean active, int page, int size, String sort) {
    actors.current();
    return PageResponse.of(
        customers
            .findAll(
                Queries.<Customer>equal("active", active)
                    .and(Queries.searchFields(q, "name", "email", "phone")),
                Queries.page(page, size, sort, Set.of("name", "createdAt"), "name"))
            .map(CustomerDtos.View::of));
  }

  @Transactional(readOnly = true)
  public CustomerDtos.View get(UUID id) {
    actors.current();
    return CustomerDtos.View.of(
        customers.findById(id).orElseThrow(() -> BusinessException.missing("Customer")));
  }

  public CustomerDtos.View create(CustomerDtos.Create request) {
    actors.current();
    validation.check(request);
    return tx.run(
        () ->
            CustomerDtos.View.of(
                customers.save(
                    new Customer(
                        UUID.randomUUID(),
                        request.name(),
                        request.email(),
                        request.phone(),
                        request.notes(),
                        clock.instant()))));
  }

  public CustomerDtos.View update(UUID id, JsonNode fields) {
    actors.current();
    Patch patch = new Patch(fields, Set.of("name", "email", "phone", "notes", "active"));
    if (patch.has("active")) actors.admin();
    return tx.run(
        () -> {
          Customer c = customers.lock(id).orElseThrow(() -> BusinessException.missing("Customer"));
          var v =
              validation.check(
                  new CustomerDtos.Create(
                      patch.text("name", c.getName(), true),
                      patch.text("email", c.getEmail(), false),
                      patch.text("phone", c.getPhone(), false),
                      patch.text("notes", c.getNotes(), false)));
          c.edit(
              v.name(),
              v.email(),
              v.phone(),
              v.notes(),
              patch.bool("active", c.getActive()),
              clock.instant());
          return CustomerDtos.View.of(c);
        });
  }
}
