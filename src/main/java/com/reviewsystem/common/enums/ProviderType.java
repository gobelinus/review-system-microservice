// ===== providerType.java (Enhanced Enum) =====
package com.reviewsystem.common.enums;

public enum ProviderType {
  AGODA("Agoda"),
  BOOKING("Booking"),
  EXPEDIA("Expedia"),
  AIRBNB("Airbnb"),
  VERY_LONG_LONG_PROVIDER_TYPE("VERY_LONG_LONG_PROVIDER_TYPE"),
  INACTIVE("Inactive");

  private final String displayName;

  ProviderType(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public static ProviderType fromString(String providerName) {
    for (ProviderType type : values()) {
      if (type.displayName.equalsIgnoreCase(providerName)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown provider: " + providerName);
  }
}
