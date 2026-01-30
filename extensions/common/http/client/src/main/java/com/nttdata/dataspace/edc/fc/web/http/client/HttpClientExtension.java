package com.nttdata.dataspace.edc.fc.web.http.client;

import okhttp3.OkHttpClient;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import javax.net.ssl.*;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

@Extension(value = "Configurable HTTP Client Extension")
public class HttpClientExtension implements ServiceExtension {

    private static final String DISABLE_TLS_CONFIG = "edc.http.disable.tls";

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        boolean disableTls = context.getConfig().getBoolean(DISABLE_TLS_CONFIG, false);

        if (disableTls) {
            monitor.warning("WARNING: TLS and hostname verification are DISABLED for HTTP clients! This should only be used in development or testing environments, and NEVER in production.");
        } else {
            monitor.info("TLS and hostname verification are ENABLED for HTTP clients.");
        }
    }

    @Provider
    public OkHttpClient createHttpClient(ServiceExtensionContext context) {
        boolean disableTls = context.getConfig().getBoolean(DISABLE_TLS_CONFIG, false);

        if (disableTls) {
            try {
                return createInsecureClient();
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Failed to initialize insecure HTTP client", e);
            }
        } else {
            // Default secure client
            return new OkHttpClient();
        }
    }

    /**
     * Creates an insecure OkHttpClient that trusts all certificates.
     * <p>
     * This should only be used in non-production environments for testing purposes. It disables all server
     * certificate validation and hostname verification, which makes it vulnerable to man-in-the-middle (MitM) attacks.
     * NEVER use this in production environments!
     * </p>
     */
    @SuppressWarnings({"java:S4830", "java:S5527"})  // Suppresses SonarQube rule for insecure SSL/TLS usage and hostname validation
    private OkHttpClient createInsecureClient() throws GeneralSecurityException {
        // Trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    // Intentionally left blank: skip client check
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    // Intentionally left blank: skip server check
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };

        // Install the all-trusting trust manager
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        return new OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .build();
    }
}