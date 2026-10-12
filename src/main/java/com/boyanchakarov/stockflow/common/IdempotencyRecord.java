package com.boyanchakarov.stockflow.common;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
  @Id private UUID id;
  @Column private UUID actorId;

  @Column(length = 40)
  private String operation;

  @Column private UUID idempotencyKey;

  @Column(columnDefinition = "char(64)")
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
  private String requestHash;

  @Column private Integer responseStatus;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  private String responseBody;

  @Column(length = 500)
  private String responseLocation;

  @Column private Instant createdAt;
  @Column private Instant completedAt;

  protected IdempotencyRecord() {}

  public UUID getId() {
    return id;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getOperation() {
    return operation;
  }

  public UUID getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public Integer getResponseStatus() {
    return responseStatus;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public String getResponseLocation() {
    return responseLocation;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
