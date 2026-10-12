package com.boyanchakarov.stockflow.inventory;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface StockMovementRepository
    extends JpaRepository<StockMovement, UUID>, JpaSpecificationExecutor<StockMovement> {}
