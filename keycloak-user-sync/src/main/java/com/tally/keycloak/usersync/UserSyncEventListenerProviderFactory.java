package com.tally.keycloak.usersync;

import java.net.URI;

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

    private static final Logger LOG = Logger.getLogger(UserSyncEventListenerProviderFactory.class);

    private URI apiUrl;
    private String apiKey;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new UserSyncEventListenerProvider(session, apiUrl, apiKey);
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
        LOG.infof("'%s' event listener will send users to %s", PROVIDER_ID, apiUrl);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
