package dev.seatsure.auth;

import dev.seatsure.auth.AuthDtos.AuthResponse;
import dev.seatsure.auth.AuthDtos.LoginRequest;
import dev.seatsure.auth.AuthDtos.RegisterRequest;
import dev.seatsure.auth.AuthDtos.UserResponse;
import dev.seatsure.common.ConflictException;
import java.time.Clock;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("That email is already registered");
        }
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.ATTENDEE);
        user.setCreatedAt(clock.instant());
        userRepository.save(user);
        return token(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElseThrow();
        return token(user);
    }

    private AuthResponse token(User user) {
        return new AuthResponse(jwtService.issue(user), jwtService.expirySeconds(), UserResponse.from(user));
    }
}
