package com.isp.zoho.notifier.exception;

public class ZohoClientException extends RuntimeException {

  private final int statusCode;

  public ZohoClientException(String message, int statusCode) {
    super(message);
    this.statusCode = statusCode;
  }

  public ZohoClientException(String message, int statusCode, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public boolean is4xx() {
    return statusCode >= 400 && statusCode < 500;
  }

  public boolean is5xx() {
    return statusCode >= 500;
  }
}
