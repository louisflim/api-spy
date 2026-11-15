package edu.citu.apispy.security;

import edu.citu.apispy.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {

	private final UserAccountRepository userAccountRepository;

	public AccountUserDetailsService(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return userAccountRepository.findByEmailIgnoreCase(username.trim())
				.map(AccountPrincipal::new)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}
}
