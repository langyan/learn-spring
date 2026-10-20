package com.lin.spring.shiro.config;

import com.lin.spring.shiro.auth.AuthorityResolver;
import com.lin.spring.shiro.auth.JwtFilter;
import com.lin.spring.shiro.auth.JwtRealm;
import com.lin.spring.shiro.auth.JwtUtil;
import jakarta.servlet.Filter;
import org.apache.shiro.mgt.DefaultSessionStorageEvaluator;
import org.apache.shiro.mgt.DefaultSubjectDAO;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Apache Shiro wiring — the direct counterpart of Spring Security's
 * SecurityConfig (filter chain + authorization rules):
 *
 * <table border="1">
 *   <tr><th>Spring Security edition</th><th>Shiro edition (this class)</th></tr>
 *   <tr><td>SecurityFilterChain</td><td>ShiroFilterFactoryBean chain map</td></tr>
 *   <tr><td>JwtAuthenticationFilter</td><td>JwtFilter ("jwt" chain entry)</td></tr>
 *   <tr><td>UserDetailsService + DaoAuthenticationProvider</td><td>JwtRealm + UserAuthService</td></tr>
 *   <tr><td>SessionCreationPolicy.STATELESS</td><td>SessionStorageEvaluator disabled</td></tr>
 *   <tr><td>@PreAuthorize("hasRole/hasAuthority")</td><td>@RequiresRoles / @RequiresPermissions (AOP)</td></tr>
 *   <tr><td>AuthenticationEntryPoint / AccessDeniedHandler</td><td>JsonFilter 401 + GlobalExceptionHandler 403</td></tr>
 * </table>
 *
 * <p>Authorization strategy: the filter chain authenticates ("jwt" on
 * everything non-public), while roles/permissions are enforced with Shiro
 * annotations so that 403s flow through the global exception handler.
 */
@Configuration
public class ShiroConfig {

    @Bean
    public JwtRealm jwtRealm(JwtUtil jwtUtil, AuthorityResolver authorityResolver) {
        return new JwtRealm(jwtUtil, authorityResolver);
    }

    /**
     * Stateless subject storage: Shiro must never create HTTP sessions
     * because authentication state lives entirely in the JWT.
     */
    @Bean
    public DefaultWebSecurityManager securityManager(JwtRealm jwtRealm) {
        DefaultWebSecurityManager manager = new DefaultWebSecurityManager(jwtRealm);

        DefaultSessionStorageEvaluator sessionStorageEvaluator = new DefaultSessionStorageEvaluator();
        sessionStorageEvaluator.setSessionStorageEnabled(false);
        DefaultSubjectDAO subjectDAO = new DefaultSubjectDAO();
        subjectDAO.setSessionStorageEvaluator(sessionStorageEvaluator);
        manager.setSubjectDAO(subjectDAO);
        return manager;
    }

    @Bean
    public ShiroFilterFactoryBean shiroFilter(SecurityManager securityManager,
                                              JwtUtil jwtUtil,
                                              ObjectMapper objectMapper) {
        ShiroFilterFactoryBean factory = new ShiroFilterFactoryBean();
        factory.setSecurityManager(securityManager);
        // must be MUTABLE: Shiro's BeanPostProcessor collects Filter beans into this map
        Map<String, Filter> filters = new LinkedHashMap<>();
        filters.put("jwt", new JwtFilter(objectMapper));
        factory.setFilters(filters);

        // first match wins — order matters
        Map<String, String> chain = new LinkedHashMap<>();
        chain.put("/api/v1/auth/**", "anon");      // login endpoint
        chain.put("/h2-console/**", "anon");       // dev console
        chain.put("/**", "jwt");                   // everything else requires a Bearer token
        factory.setFilterChainDefinitionMap(chain);
        return factory;
    }

    /**
     * Enables @RequiresPermissions / @RequiresRoles via Spring AOP.
     * The @Lazy proxy is essential: advisors are instantiated during the
     * BeanPostProcessor registration phase, and without the indirection the
     * SecurityManager (-> Realm -> AuthorityResolver -> JPA repository) would
     * be created before the entityManagerFactory infrastructure exists.
     */
    @Bean
    public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(
            @Lazy SecurityManager securityManager) {
        AuthorizationAttributeSourceAdvisor advisor = new AuthorizationAttributeSourceAdvisor();
        advisor.setSecurityManager(securityManager);
        return advisor;
    }

    /** Shiro lifecycle (init/destroy) support for spring beans.
     *  static: must not trigger eager initialization of other beans. */
    @Bean
    public static LifecycleBeanPostProcessor lifecycleBeanPostProcessor() {
        return new LifecycleBeanPostProcessor();
    }
}
