package com.isp.zoho.notifier.service;

import com.isp.zoho.notifier.repository.ZohoNotificationSentRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetentionScheduler {

  private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);
  private static final int RETENTION_DAYS = 90;

  private final ZohoNotificationSentRepository repository;

  public RetentionScheduler(ZohoNotificationSentRepository repository) {
    this.repository = repository;
  }

  @Scheduled(cron = "0 0 2 * * *")
  public void purgeOldRecords() {
    Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
    int deleted = repository.deleteOlderThan(cutoff);
    log.info("Retention purge: deleted {} records older than {} days", deleted, RETENTION_DAYS);
  }
}
