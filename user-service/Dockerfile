FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
RUN chmod +x gradlew

COPY user-service/build.gradle user-service/build.gradle
COPY wallet-service/build.gradle wallet-service/build.gradle
COPY api-gateway/build.gradle api-gateway/build.gradle
COPY tally-common tally-common
COPY user-service/src user-service/src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :user-service:bootJar --no-daemon \
 && cp user-service/build/libs/*-SNAPSHOT.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
WORKDIR /application

RUN groupadd --system app \
 && useradd --system --gid app --no-create-home app \
 && mkdir logs && chown app:app logs

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "application.jar"]
