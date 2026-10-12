package com.boyanchakarov.stockflow.order;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface OrderEventRepository
    extends JpaRepository<OrderEvent, UUID>, JpaSpecificationExecutor<OrderEvent> {
  List<OrderEvent> findByOrderIdOrderByCreatedAtAscIdAsc(UUID orderId);
}
