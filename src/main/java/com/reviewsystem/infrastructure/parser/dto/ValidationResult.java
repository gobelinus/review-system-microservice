// ===== ValidationResult.java =====
package com.reviewsystem.infrastructure.parser.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
  private boolean valid;
  private List<String> errors;

  public static ValidationResult valid() {
    return ValidationResult.builder().valid(true).errors(List.of()).build();
  }

  public static ValidationResult invalid(List<String> errors) {
    return ValidationResult.builder().valid(false).errors(errors).build();
  }
}
