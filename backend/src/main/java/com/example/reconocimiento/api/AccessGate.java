package com.example.reconocimiento.api;

import com.example.reconocimiento.application.AccessService;
import com.example.reconocimiento.domain.RuleViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import static com.example.reconocimiento.domain.AccessModels.Role;

@Configuration
public class AccessGate implements WebMvcConfigurer {
    static final String PRINCIPAL = "accessPrincipal";
    enum Level { PUBLIC, SESSION }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Allow { Level value(); }

    private final AccessService access;

    public AccessGate(AccessService access) { this.access = access; }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!(handler instanceof HandlerMethod method)) return true;
                response.setHeader("Cache-Control", "no-store");
                Allow allow = method.getMethodAnnotation(Allow.class);
                if (allow != null && allow.value() == Level.PUBLIC) return true;
                var principal = access.requireSession(request.getHeader("Authorization"));
                if (allow == null && principal.role() != Role.ADMINISTRADOR) {
                    throw new RuleViolation("SOLO_ADMINISTRADOR", "Esta opción requiere acceso de administrador.", 403);
                }
                request.setAttribute(PRINCIPAL, principal);
                return true;
            }
        }).addPathPatterns("/api/**");
    }
}
