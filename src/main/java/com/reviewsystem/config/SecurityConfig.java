package com.reviewsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  public UserDetailsService userDetailsService() {
    // Define a normal user
    UserDetails user =
        User.withUsername("user")
            .password("password") // plaintext password for demo
            .roles("USER") // ROLE_USER
            .build();

    // Define an admin user
    UserDetails admin =
        User.withUsername("admin")
            .password("adminpass") // plaintext password for demo
            .roles("ADMIN") // ROLE_ADMIN
            .build();

    return new InMemoryUserDetailsManager(user, admin);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    // Not secure: passwords stored in plain text; only for dev/testing
    return NoOpPasswordEncoder.getInstance();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf()
        .disable() // Disable CSRF for simplicity; re-enable for production if needed
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/public/**")
                    .permitAll() // Public endpoints
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN") // Admin-only endpoints
                    .anyRequest()
                    .authenticated() // All other endpoints require authentication
            )
        .formLogin(form -> form.permitAll())
        .logout(logout -> logout.permitAll());

    return http.build();
  }
}
