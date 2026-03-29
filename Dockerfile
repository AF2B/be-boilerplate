# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM clojure:tools-deps-alpine AS builder

ARG APP_VERSION=dev

WORKDIR /app

COPY deps.edn .
RUN clojure -P && clojure -P -T:build

COPY . .
RUN APP_VERSION=${APP_VERSION} clojure -T:build uber


# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

ARG APP_VERSION=dev
ARG PORT=8080
ARG PROFILE=prod

ENV APP_VERSION=${APP_VERSION}
ENV PORT=${PORT}
ENV PROFILE=${PROFILE}

RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app

COPY --from=builder /app/target/app.jar app.jar

EXPOSE ${PORT}

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT}/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
