# ─────────────────────────────────────────────────────────────
#  Build stage
# ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies first so they stay cached when only sources change.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ─────────────────────────────────────────────────────────────
#  Runtime stage
#
#  ta4j 0.22.6 targets Java 21 — do not bump the JRE below 21,
#  and do not upgrade ta4j past 0.22.6 without checking bytecode.
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app \
    && apk add --no-cache curl

# Quarkus fast-jar layout: lib/ first (changes least), app code last.
COPY --from=build --chown=app:app /build/target/quarkus-app/lib/     ./lib/
COPY --from=build --chown=app:app /build/target/quarkus-app/*.jar    ./
COPY --from=build --chown=app:app /build/target/quarkus-app/app/     ./app/
COPY --from=build --chown=app:app /build/target/quarkus-app/quarkus/ ./quarkus/

USER app
EXPOSE 8080

# The workload is tiny — a small heap keeps the free-tier VM comfortable.
ENV JAVA_OPTS="-Xms128m -Xmx512m -XX:+UseSerialGC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/quarkus-run.jar"]
