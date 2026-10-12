package com.boyanchakarov.stockflow.auth;

import com.boyanchakarov.stockflow.common.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService userDetailsService(AppUserRepository users) {
    return name -> {
      var u =
          users
              .findByUsername(Text.username(name))
              .filter(AppUser::getEnabled)
              .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
      return User.withUsername(u.getUsername())
          .password(u.getPasswordHash())
          .roles(u.getRole())
          .build();
    };
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, Problems problems) throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/login", "/css/**", "/js/**", "/api/v1/csrf", "/actuator/health", "/error")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/inventory/**",
                        "/inventory/**",
                        "/products/new",
                        "/products/*/edit")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/products")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/products/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginPage("/login")
                    .defaultSuccessUrl("/", true)
                    .failureUrl("/login?error")
                    .permitAll())
        .logout(l -> l.logoutSuccessUrl("/login?logout"))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          if (req.getRequestURI().startsWith("/api/"))
                            problems.write(
                                req, res, 401, "UNAUTHENTICATED", "Sign in to continue.");
                          else res.sendRedirect("/login");
                        })
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            problems.write(
                                req,
                                res,
                                403,
                                "FORBIDDEN",
                                "Insufficient permission or missing/invalid CSRF token.")));
    return http.build();
  }
}
