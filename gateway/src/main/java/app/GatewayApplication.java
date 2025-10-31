package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
@EnableScheduling  // bật scheduler
public class GatewayApplication {
    public static void main(String[] args) {
        System.out.println("Starting " + GatewayApplication.class);
        SpringApplication.run(GatewayApplication.class, args);
    }

    // Log định kỳ mỗi 10 giây
    @Scheduled(fixedRate = 10000)
    public void heartbeat() {
        System.out.println("💓 Server is alive: " + System.currentTimeMillis());
    }

    @Configuration
    public static class CorsConfig {
        @Bean
        public WebMvcConfigurer corsConfigurer() {
            return new WebMvcConfigurer() {
                @Override
                public void addCorsMappings(CorsRegistry registry) {
                    registry.addMapping("/**")
                            .allowedOriginPatterns("*")
                            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                            .allowedHeaders("*")
                            .allowCredentials(false)
                            .exposedHeaders("*");
                }
            };
        }
    }
}
