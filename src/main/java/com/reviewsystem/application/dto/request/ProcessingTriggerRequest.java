package com.reviewsystem.application.dto.request;

import com.reviewsystem.common.enums.ProviderType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingTriggerRequest {

  @NotNull(message = "Provider is required")
  private ProviderType provider;

  @Builder.Default private boolean forceReprocess = false;

  @Min(value = 1, message = "Max files must be at least 1")
  @Builder.Default
  private Integer maxFiles = 100;

  /** Whether to process asynchronously (true) or synchronously (false) */
  @Builder.Default private boolean asynchronous = false;

  /** Who or what triggered this processing request */
  @Size(max = 100, message = "Triggered by cannot exceed 100 characters")
  private String triggeredBy;

  private String s3Path;

  private String s3Prefix;

  private boolean skipValidation = false;
}
