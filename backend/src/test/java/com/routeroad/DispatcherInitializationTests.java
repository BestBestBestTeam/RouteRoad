package com.routeroad;

import com.routeroad.auth.application.*;
import com.routeroad.shared.domain.AppUserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DispatcherInitializationTests {
    @Test void missingCredentialsFailClosed() {
        var store = mock(AccountStore.class);
        var encoder = mock(PasswordEncoder.class);
        assertThatThrownBy(() -> new AccountService(store, encoder).initializeDispatcher("", "", ""))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(store, encoder);
    }
    @Test void existingDispatcherIsUnchanged() {
        var store = mock(AccountStore.class);
        var encoder = mock(PasswordEncoder.class);
        when(store.findByLogin("existing")).thenReturn(Optional.of(new Account(UUID.randomUUID(), "existing", "Name", AppUserRole.DISPATCHER, null)));
        new AccountService(store, encoder).initializeDispatcher("existing", "explicit-secret", "New name");
        verifyNoInteractions(encoder);
        verify(store, never()).create(any(), any(), any(), any(), any());
    }
    @Test void occupiedRepresentativeLoginCannotBecomeDispatcher() {
        var store = mock(AccountStore.class);
        when(store.findByLogin("existing")).thenReturn(Optional.of(new Account(UUID.randomUUID(), "existing", "Name", AppUserRole.REPRESENTATIVE, UUID.randomUUID())));
        assertThatThrownBy(() -> new AccountService(store, mock(PasswordEncoder.class))
                .initializeDispatcher("existing", "explicit-secret", "New name")).isInstanceOf(IllegalStateException.class);
        verify(store, never()).create(any(), any(), any(), any(), any());
    }
}
