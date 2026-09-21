    package com.flashsale.commons.security;

    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
    import org.springframework.security.config.annotation.web.builders.HttpSecurity;
    import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
    import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
    import org.springframework.security.config.http.SessionCreationPolicy;
    import org.springframework.security.web.SecurityFilterChain;
    import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

    import org.springframework.core.io.Resource;

    import java.io.InputStream;
    import java.nio.charset.StandardCharsets;
    import java.security.PublicKey;

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    public class SecurityConfig {

        @Value("${security.jwt.public-key}")
        private String rawPublicKey;

        @Bean
        public PublicKey rsaPublicKey(@Value("${security.jwt.public-key}") Resource publicKeyResource) throws Exception {
            try (InputStream inputStream = publicKeyResource.getInputStream()) {
                String keyContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                return KeyUtils.parsePublicKey(keyContent);
            }
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http, PublicKey publicKey) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                            .anyRequest().authenticated()
                    )
                    .addFilterBefore(new JwtAuthenticationFilter(publicKey), UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }