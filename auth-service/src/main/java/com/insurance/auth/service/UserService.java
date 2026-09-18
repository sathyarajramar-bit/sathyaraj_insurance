package com.insurance.auth.service;

import com.insurance.auth.dto.UserResponse;
import com.insurance.auth.entity.Role;
import com.insurance.auth.entity.User;
import com.insurance.auth.mapper.UserMapper;
import com.insurance.auth.repository.UserRepository;
import com.insurance.common.dto.PageResponse;
import com.insurance.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userMapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String email, Pageable pageable) {
        String fragment = email == null ? "" : email.trim();
        return PageResponse.from(userRepository.findByEmailContainingIgnoreCase(fragment, pageable), userMapper::toResponse);
    }

    /** Replaces the role set. Dirty checking flushes the change at commit; @Version guards concurrent edits. */
    @Transactional
    public UserResponse assignRoles(Long id, Set<Role> roles) {
        User user = find(id);
        user.setRoles(new HashSet<>(roles));
        log.info("Roles of user {} set to {}", id, roles);
        return userMapper.toResponse(user);
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
