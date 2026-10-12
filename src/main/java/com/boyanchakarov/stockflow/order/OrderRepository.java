package com.boyanchakarov.stockflow.order;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderRepository
    extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.id=:id")
  Optional<Order> lock(@Param("id") UUID id);
}
