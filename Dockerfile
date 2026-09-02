# One image serving both halves: the API and the page that reads it.
#
# The frontend is compiled to static files and handed to Spring rather than run behind its own
# server. There is one user, one machine, and no reason for a second process.

# --- the page ---
FROM node:22-alpine AS frontend
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- the API ---
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
# Dependencies resolve in their own layer, so a source change does not refetch the internet.
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
# Tests need Docker and a database; they run in development, not while building the image.
RUN mvn -q -B package -DskipTests

# --- what actually ships ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app

COPY --from=backend /build/target/*.jar app.jar
COPY --from=frontend /build/dist ./static

# Credentials are mounted or passed in, never baked into the image.
# Relaxed binding strips the dash: spring.web.resources.static-locations becomes
# SPRING_WEB_RESOURCES_STATICLOCATIONS, not ..._STATIC_LOCATIONS.
ENV SPRING_WEB_RESOURCES_STATICLOCATIONS=file:/app/static/
ENV JAVA_OPTS=""

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget -qO- http://localhost:8080/api/mirror/status > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
