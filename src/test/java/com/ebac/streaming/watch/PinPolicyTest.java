package com.ebac.streaming.watch;

import com.ebac.streaming.exception.InvalidPinException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinPolicyTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final PinPolicy policy = new PinPolicy(encoder);

    @Test
    void correctPinPasses() {
        String hash = encoder.encode("1234");

        assertThatCode(() -> policy.validate("1234", hash))
                .doesNotThrowAnyException();
    }

    @Test
    void wrongPinThrows() {
        String hash = encoder.encode("1234");

        assertThatThrownBy(() -> policy.validate("9999", hash))
                .isInstanceOf(InvalidPinException.class);
    }
}