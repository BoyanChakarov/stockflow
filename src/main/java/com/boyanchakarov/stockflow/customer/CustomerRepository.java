package com.boyanchakarov.stockflow.customer;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository
    extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Customer c where c.id=:id")
  Optional<Customer> lock(@Param("id") UUID id);
}
