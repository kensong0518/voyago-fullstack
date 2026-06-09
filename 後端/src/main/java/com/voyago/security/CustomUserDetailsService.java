package com.voyago.security;

import com.voyago.entity.Member;
import com.voyago.repository.MemberDao;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/** Spring Security 以此載入會員帳密供 AuthenticationManager 驗證。 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberDao members;

    public CustomUserDetailsService(MemberDao members) {
        this.members = members;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Member m = members.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("查無此帳號"));
        // Google 註冊者沒有密碼，給一個永遠不會比對成功的佔位字串
        String password = m.getPassword() != null ? m.getPassword() : "N/A";
        return User.withUsername(m.getEmail())
                .password(password)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + m.getRole())))
                .build();
    }
}
