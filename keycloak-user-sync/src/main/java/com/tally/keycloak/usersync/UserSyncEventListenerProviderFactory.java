package com.tally.keycloak.usersync;

import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/**
 * Registers the {@code user-sync} event listener. Configured through the
 * {@code USER_SYNC_API_URL} and {@code USER_SYNC_API_KEY} environment variables.
 */
public class UserSyncEventListenerProviderFactory implements EventListenerProviderFactory {

    public static final String PROVIDER_ID = "user-sync";

    static final String ENV_API_URL = "USER_SYNC_API_URL";
    static final String ENV_API_KEY = "USER_SYNC_API_KEY";

    // Bounded, so syncs queued while the backend hangs cannot exhaust memory; overflow is dropped and logged.
    private static final int SENDER_THREADS = 2;
    private static final int SENDER_QUEUE_CAPACITY = 1000;

    private static final Logger LOG = Logger.getLogger(UserSyncEventListenerProviderFactory.class);

    private URI apiUrl;
    private String apiKey;
    private ThreadPoolExecutor sender;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new UserSyncEventListenerProvider(session, apiUrl, apiKey, sender);
    }

    @Override
    public void init(Config.Scope config) {
        String url = System.getenv(ENV_API_URL);
        apiKey = blankToNull(System.getenv(ENV_API_KEY));

        if (blankToNull(url) == null) {
            LOG.warnf("%s is not set; the '%s' event listener will do nothing", ENV_API_URL, PROVIDER_ID);
            return;
        }
        try {
            apiUrl = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            LOG.warnf("%s is not a valid URL (%s); the '%s' event listener will do nothing",
                    ENV_API_URL, url, PROVIDER_ID);
            return;
        }
        if (apiKey == null) {
            LOG.warnf("%s is not set; requests will be sent without the X-Api-Key header", ENV_API_KEY);
        }
        sender = newSender();
        LOG.infof("'%s' event listener will send users to %s", PROVIDER_ID, apiUrl);
    }

    private static ThreadPoolExecutor newSender() {
        AtomicInteger count = new AtomicInteger();
        return new ThreadPoolExecutor(SENDER_THREADS, SENDER_THREADS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(SENDER_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, PROVIDER_ID + "-sender-" + count.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
        if (sender == null) {
            return;
        }
        sender.shutdown();
        try {
            if (!sender.awaitTermination(5, TimeUnit.SECONDS)) {
                sender.shutdownNow();
            }
        } catch (InterruptedException e) {
            sender.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
