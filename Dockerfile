# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM clojure:temurin-21-tools-deps AS builder

ARG APP_VERSION=dev

WORKDIR /app

COPY deps.edn build.clj ./
RUN clojure -P && clojure -P -T:build

COPY src ./src
RUN APP_VERSION=${APP_VERSION} clojure -T:build uber


# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

ARG APP_VERSION=dev

# No PROFILE here on purpose: the orchestrator sets it, and a container
# without one exits with status 2 instead of starting with the wrong config.
ENV APP_VERSION=${APP_VERSION}
ENV PORT=8080

RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app

COPY --from=builder /app/target/app.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT}/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
