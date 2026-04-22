package com.isp.zoho.notifier.repository;

import com.isp.zoho.notifier.model.entity.ZohoNotificationSent;
import com.isp.zoho.notifier.model.entity.ZohoNotificationSent.NotificationStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ZohoNotificationSentRepository
    extends JpaRepository<ZohoNotificationSent, String> {

  Optional<ZohoNotificationSent> findByRequestIdAndStatus(
      String requestId, NotificationStatus status);

  boolean existsByRequestIdAndStatus(String requestId, NotificationStatus status);

  @Transactional
  @Modifying
  @Query("DELETE FROM ZohoNotificationSent z WHERE z.sentAt < :cutoff")
  int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
