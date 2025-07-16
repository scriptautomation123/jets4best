FROM openjdk:21-jre-slim

# Create app user for security
RUN useradd -r -u 1001 -g root appuser

# Set working directory
WORKDIR /app

# Copy the built JAR file
COPY target/aieutil-*-jar-with-dependencies.jar app.jar

# Copy configuration files
COPY src/main/resources/application.yaml ./
COPY src/main/resources/vaults.yaml ./

# Set ownership to appuser
RUN chown -R 1001:0 /app && chmod -R g=u /app

# Switch to non-root user
USER 1001

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD ["java", "-cp", "app.jar", "com.baml.mav.aieutil.ExecProcCmd", "--help"]

# Entry point
ENTRYPOINT ["java", "-jar", "app.jar"] 