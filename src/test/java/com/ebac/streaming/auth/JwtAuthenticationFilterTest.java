package com.ebac.streaming.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService =
            new JwtService(new JwtProperties("TEST_SECRET_TEST_SECRET_TEST_SECRET_32B", 60));
    private final AccountUserDetailsService userDetailsService = mock(AccountUserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);

    private UUID accountId;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithAuthorization(String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", headerValue);
        return request;
    }

    @Test
    void validTokenAuthenticatesTheAccountWithItsAuthorities() throws Exception {
        when(userDetailsService.loadUserByUsername(accountId.toString())).thenReturn(
                new User(accountId.toString(), "unused", List.of(() -> "ROLE_SUBSCRIBER")));

        filter.doFilter(requestWithAuthorization("Bearer " + jwtService.generateToken(accountId)),
                new MockHttpServletResponse(), chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(accountId.toString());
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_SUBSCRIBER");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void invalidTokenLeavesRequestUnauthenticatedAndContinuesTheChain() throws Exception {
        filter.doFilter(requestWithAuthorization("Bearer not.a.valid-token"),
                new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void nonBearerHeaderIsIgnored() throws Exception {
        filter.doFilter(requestWithAuthorization("Basic dXNlcjpwYXNz"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void validTokenForADeletedAccountLeavesRequestUnauthenticated() throws Exception {
        when(userDetailsService.loadUserByUsername(anyString()))
                .thenThrow(new UsernameNotFoundException("Account not found."));

        filter.doFilter(requestWithAuthorization("Bearer " + jwtService.generateToken(accountId)),
                new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }
}
