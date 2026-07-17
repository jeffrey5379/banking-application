package com.bankapp.security;

import com.bankapp.exception.InvalidOtpException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpStoreTest {

    private final OtpStore store = new OtpStore();

    @Test
    void correctCode_returnsUsernameAndConsumesChallenge() {
        String token = store.createChallenge("alice", "111111");

        String username = store.verify(token, "111111");

        assertThat(username).isEqualTo("alice");
    }

    @Test
    void challengeIsSingleUse_secondVerifyFails() {
        String token = store.createChallenge("alice", "111111");
        store.verify(token, "111111");

        assertThatThrownBy(() -> store.verify(token, "111111"))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void wrongCode_throwsAndChallengeStaysAliveUntilAttemptsExhausted() {
        String token = store.createChallenge("alice", "111111");

        assertThatThrownBy(() -> store.verify(token, "000000"))
                .isInstanceOf(InvalidOtpException.class);

        // still usable with the right code before hitting the attempt cap
        String username = store.verify(token, "111111");
        assertThat(username).isEqualTo("alice");
    }

    @Test
    void tooManyWrongAttempts_invalidatesChallengeEvenWithCorrectCodeAfter() {
        String token = store.createChallenge("alice", "111111");

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> store.verify(token, "000000"))
                    .isInstanceOf(InvalidOtpException.class);
        }

        assertThatThrownBy(() -> store.verify(token, "111111"))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void unknownToken_throws() {
        assertThatThrownBy(() -> store.verify("does-not-exist", "111111"))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void expiredChallenge_throws() throws InterruptedException {
        OtpStore shortLived = new OtpStore(20);
        String token = shortLived.createChallenge("alice", "111111");

        Thread.sleep(50);

        assertThatThrownBy(() -> shortLived.verify(token, "111111"))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void differentChallenges_areIndependent() {
        String token1 = store.createChallenge("alice", "111111");
        String token2 = store.createChallenge("bob", "222222");

        assertThat(store.verify(token1, "111111")).isEqualTo("alice");
        assertThat(store.verify(token2, "222222")).isEqualTo("bob");
    }
}
