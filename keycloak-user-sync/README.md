# keycloak-user-sync

A Keycloak event listener (`user-sync`) that POSTs a user's profile to the backend after
`REGISTER`, `LOGIN`, `UPDATE_PROFILE` or `VERIFY_PROFILE`. It is built for Keycloak **26.7.4**
(`keycloakVersion` in `gradle.properties`, which must match the server).

Body sent to `USER_SYNC_API_URL` with header `X-Api-Key: $USER_SYNC_API_KEY`:

```json
{ "subkey": "<user id>", "firstName": "...", "lastName": "...", "email": "...",
  "phoneNumber": "...", "address": "..." }
```

The request is sent on a background thread (2 threads, queue of 1000), so a slow or unavailable
backend never delays the login. Failures are logged and dropped; because every login syncs again,
a user missed while the backend was down is caught up on their next login.

## Build

```bash
./gradlew build
```

Output: `build/libs/keycloak-user-sync-1.0.0.jar`. It includes no dependencies, because Keycloak provides them at runtime.

## Deploy

1. **Copy the JAR** into `/opt/keycloak/providers/`. With Docker Compose, mount it:

   ```yaml
   keycloak:
     image: quay.io/keycloak/keycloak:26.7.4
     volumes:
       - ./keycloak-user-sync/build/libs/:/opt/keycloak/providers/:ro
   ```

   Build the JAR before starting the container. Mounting the directory instead of the file avoids a Docker gotcha:
   if the JAR doesn't exist yet, Docker creates an empty directory with the JAR's name.

2. **Set the environment variables** on the Keycloak process:

   ```yaml
     environment:
       USER_SYNC_API_URL: http://user-service:8080/api/v1/clients
       USER_SYNC_API_KEY: ${USER_SYNC_API_KEY}  # from .env
   ```

   If `USER_SYNC_API_URL` is missing, Keycloak logs one warning at startup and the listener does nothing.

3. **Build and restart**: run `/opt/keycloak/bin/kc.sh build`, then restart Keycloak.
   `start-dev` re-runs the build on startup, so a restart is enough in dev mode.

4. **Enable it**: Admin console, realm `tally`, then *Realm settings > Events > Event listeners*.
   Add `user-sync` and save.
