package com.nttdata.dataspace.edc.fc.web.http.client;

import okhttp3.OkHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class HttpClientExtensionTest {

    private HttpClientExtension extension;
    private ServiceExtensionContext context;
    private Monitor monitor;
    private Config config;

    @BeforeEach
    void setUp() {
        extension = new HttpClientExtension();
        context = mock(ServiceExtensionContext.class);
        monitor = mock(Monitor.class);
        config = mock(Config.class);
        
        when(context.getMonitor()).thenReturn(monitor);
        when(context.getConfig()).thenReturn(config);
    }

    @Test
    void initialize_shouldLogWarning_whenTlsIsDisabled() {
        when(config.getBoolean("edc.http.disable.tls", false)).thenReturn(true);

        extension.initialize(context);

        verify(monitor).warning(contains("WARNING: TLS and hostname verification are DISABLED"));
        verify(monitor).warning(contains("NEVER in production"));
        verify(monitor, never()).info(anyString());
    }

    @Test
    void initialize_shouldLogInfo_whenTlsIsEnabled() {
        when(config.getBoolean("edc.http.disable.tls", false)).thenReturn(false);

        extension.initialize(context);

        verify(monitor).info(contains("TLS and hostname verification are ENABLED"));
        verify(monitor, never()).warning(anyString());
    }

    @Test
    void initialize_shouldLogInfo_whenTlsConfigIsNotSet() {
        when(config.getBoolean("edc.http.disable.tls", false)).thenReturn(false);

        extension.initialize(context);

        verify(monitor).info(contains("TLS and hostname verification are ENABLED"));
    }

    @Test
    void createHttpClient_shouldReturnSecureClient_whenTlsIsEnabled() {
        when(config.getBoolean("edc.http.disable.tls", false)).thenReturn(false);

        OkHttpClient client = extension.createHttpClient(context);

        assertThat(client).isNotNull();
        assertThat(client.hostnameVerifier()).isNotNull();
        // Secure client uses default hostname verifier
    }

    @Test
    void createHttpClient_shouldReturnInsecureClient_whenTlsIsDisabled() {
        when(config.getBoolean("edc.http.disable.tls", false)).thenReturn(true);

        OkHttpClient client = extension.createHttpClient(context);

        assertThat(client).isNotNull();
        assertThat(client.hostnameVerifier()).isNotNull();
        assertThat(client.sslSocketFactory()).isNotNull();
        // Insecure client trusts all certificates
        assertThat(client.hostnameVerifier().verify("any-hostname", null)).isTrue();
    }

    @Test
    void createHttpClient_shouldReturnDifferentInstances_whenCalledMultipleTimes() {
        when(config.getBoolean("edc.http.disable.tls", false)).thenReturn(false);

        OkHttpClient client1 = extension.createHttpClient(context);
        OkHttpClient client2 = extension.createHttpClient(context);

        assertThat(client1).isNotSameAs(client2);
    }

    @Test
    void createHttpClient_insecureMode_shouldTrustAllCertificates() {
        when(config.getBoolean("edc.http.disable.tls", false)).thenReturn(true);

        OkHttpClient client = extension.createHttpClient(context);

        assertThat(client).isNotNull();
        assertThat(client.sslSocketFactory()).isNotNull();
        // Verify hostname verifier accepts any hostname
        assertThat(client.hostnameVerifier().verify("localhost", null)).isTrue();
        assertThat(client.hostnameVerifier().verify("example.com", null)).isTrue();
        assertThat(client.hostnameVerifier().verify("192.168.1.1", null)).isTrue();
    }
}
