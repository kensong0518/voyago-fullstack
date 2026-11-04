package com.voyago.config;

import com.voyago.security.AuthRateLimitFilter;
import com.voyago.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity   // 啟用 @PreAuthorize，給 STAFF 路由用
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AuthRateLimitFilter authRateLimitFilter;
    private final ApiRateLimitFilter apiRateLimitFilter;

    /**
     * Google 登入（GSI）官方建議的來源：script／frame／connect／style 各自限定在 accounts.google.com/gsi/。
     * 圖片允許所有 https（行程圖在 Unsplash、頭像在 googleusercontent）；樣式需要 'unsafe-inline'（Google 登入按鈕會插入行內樣式）。
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self' https://accounts.google.com/gsi/client",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://accounts.google.com/gsi/style",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data: blob: https:",
            "connect-src 'self' wss: https://accounts.google.com/gsi/",
            "frame-src https://accounts.google.com/gsi/",
            "frame-ancestors 'none'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'");

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, AuthRateLimitFilter authRateLimitFilter,
                          ApiRateLimitFilter apiRateLimitFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authRateLimitFilter = authRateLimitFilter;
        this.apiRateLimitFilter = apiRateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(c -> c.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 瀏覽器端的防護標頭：
            //   CSP：只允許載入自己網站與 Google 登入、Google 字型的程式碼，就算有人塞進惡意內容也跑不起來（防 XSS 偷 token）；
            //   frame-ancestors 'none'：不准別的網站用 iframe 把本站框起來騙使用者點擊（clickjacking）；
            //   HSTS：瀏覽器之後一律用 HTTPS 連線；nosniff、Referrer-Policy、Permissions-Policy 降低資訊外洩。
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .frameOptions(f -> f.deny())
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(pp -> pp.policy("camera=(), microphone=(), geolocation=(), payment=()")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/ws-chat/**").permitAll()   // WS 握手走獨立的 JWT 驗證（JwtHandshakeInterceptor），非一般 header
                .requestMatchers(HttpMethod.GET, "/api/routes/**").permitAll()   // 含行程評價列表
                .requestMatchers(HttpMethod.GET, "/api/reviews/latest").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/ai/status").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/ai/advisor").permitAll()   // 免登入，用量由 AiUsageLimiter 控制
                .requestMatchers("/error").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                // 其餘 API 一律要登入；細部角色（STAFF）由 @PreAuthorize 把關
                .requestMatchers("/api/**").authenticated()
                // 前端網頁與靜態檔（同一個服務提供，見 SpaWebConfig）。頁面本身不含資料，
                // 資料都要透過上面受保護的 /api 取得，所以 /admin 這類頁面公開也拿不到後台資料
                .requestMatchers(HttpMethod.GET, "/**").permitAll()
                .anyRequest().denyAll()
            )
            .addFilterBefore(apiRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition", "X-Row-Count"));   // CSV 下載要讀檔名與筆數
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
