package com.cms.backend.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record AuthenticatedUser(
        UUID id,
        String email,
        UUID organizationId,
        boolean platformAdmin,
        List<String> roles,
        List<String> permissions
) {

    public AuthenticatedUser(UUID id, String email, UUID organizationId) {
        this(id, email, organizationId, false, List.of(), List.of());
    }

    public boolean can(String permission) {
        return platformAdmin || permissions.contains(permission) || permissions.contains("*");
    }

    public Collection<GrantedAuthority> authorities() {
        List<GrantedAuthority> granted = new ArrayList<>();
        granted.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (platformAdmin) {
            granted.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
        }
        for (String role : roles) {
            granted.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)));
        }
        for (String permission : permissions) {
            granted.add(new SimpleGrantedAuthority(permission));
        }
        return granted;
    }
}
