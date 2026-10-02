package zw.co.zimfete.assetfinance.security;

import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import zw.co.zimfete.assetfinance.config.ZimfeteProperties;

@RestController
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final OfficeAccess officeAccess;
    private final boolean demo;

    public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
                          OfficeAccess officeAccess, ZimfeteProperties properties) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.officeAccess = officeAccess;
        this.demo = properties.fineract().isDemo();
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record Me(String username, long officeId, Long staffId, Set<Role> roles, boolean allOffices, boolean demo) {
    }

    /** Sets the XSRF-TOKEN cookie. The web app calls this once before logging in. */
    @GetMapping("/api/auth/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<Me> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                                    HttpServletResponse response) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).build();
        }
        // New session id on login (prevents session fixation).
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        request.getSession(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return ResponseEntity.ok(me((AppUser) auth.getPrincipal()));
    }

    @GetMapping("/api/me")
    public Me me(@AuthenticationPrincipal AppUser user, CsrfToken token) {
        token.getToken();
        return me(user);
    }

    private Me me(AppUser user) {
        return new Me(user.username(), user.officeId(), user.staffId(), user.roles(), officeAccess.seesAllOffices(user),
                demo);
    }
}
