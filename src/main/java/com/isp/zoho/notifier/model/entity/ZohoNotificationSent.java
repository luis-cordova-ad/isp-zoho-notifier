package com.isp.zoho.notifier.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "isp_zoho_notifier_sent")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZohoNotificationSent {

  @Id
  @Column(name = "request_id", length = 36, nullable = false)
  private String requestId;

  @Column(name = "zoho_account_id", length = 255, nullable = false)
  private String zohoAccountId;

  @Column(name = "action", length = 50)
  private String action;

  @Column(name = "zoho_note_id", length = 255)
  private String zohoNoteId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, columnDefinition = "ENUM('SENT','FAILED')")
  private NotificationStatus status;

  @Column(name = "sent_at", nullable = false)
  private Instant sentAt;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  public enum NotificationStatus {
    SENT, FAILED
  }
}
