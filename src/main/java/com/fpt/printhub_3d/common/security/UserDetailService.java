package com.fpt.printhub_3d.common.security;


import com.fpt.printhub_3d.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserDetailService implements UserDetailsService {

    private final UserRepository userRepository;
    @Override
    public UserDetails loadUserByUsername(String userNameOrEmail) throws UsernameNotFoundException {
        return userRepository.findByEmailOrUsername(userNameOrEmail, userNameOrEmail)
                .map(CustomUserDetail::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + userNameOrEmail));
    }
}