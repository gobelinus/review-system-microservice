package com.reviewsystem.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  private boolean success;
  private T data;
  private String message;
  private ErrorDetails error;
  private LocalDateTime timestamp;

  private ApiResponse(boolean success, T data, String message, ErrorDetails error) {
    this.success = success;
    this.data = data;
    this.message = message;
    this.error = error;
    this.timestamp = LocalDateTime.now();
  }

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null, null);
  }

  public static <T> ApiResponse<T> success(T data, String message) {
    return new ApiResponse<>(true, data, message, null);
  }

  public static <T> ApiResponse<T> error(String code, String message) {
    return new ApiResponse<>(false, null, null, new ErrorDetails(code, message));
  }

  @Data
  public static class ErrorDetails {
    private String code;
    private String message;

    public ErrorDetails(String code, String message) {
      this.code = code;
      this.message = message;
    }
  }
}
