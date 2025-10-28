package app;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors
                        .configurationSource(request -> {
                            var cor = new org.springframework.web.cors.CorsConfiguration();
                            cor.setAllowedOrigins(java.util.List.of("http://localhost:3000"));
                            cor.setAllowedMethods(java.util.List.of("GET","POST","PUT","DELETE","OPTIONS"));
                            cor.setAllowCredentials(true);
                            cor.setAllowedHeaders(java.util.List.of("*"));
                            return cor;
                        })
                )
                .csrf(csrf -> csrf.disable()) // dev: disable CSRF
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/upload", "/api/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(withDefaults());

        return http.build();
    }
}
