package com.boyanchakarov.stockflow.auth;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AppUserRepository
    extends JpaRepository<AppUser, UUID>, JpaSpecificationExecutor<AppUser> {
  Optional<AppUser> findByUsername(String username);
}
