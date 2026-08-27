package com.pos.common.security;

import com.pos.auth.security.CustomUserDetails;
import com.pos.users.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("storeScopeEvaluator")
public class StoreScopeEvaluator {

    private final UserRepository userRepository;

    public StoreScopeEvaluator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean canAccess(UUID storeId) {
        if (storeId == null) {
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof CustomUserDetails userDetails)) {
            return false;
        }

        return userRepository.hasStoreAccess(userDetails.getId(), storeId);
    }
}
