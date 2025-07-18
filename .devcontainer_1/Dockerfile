# Create .devcontainer/Dockerfile
FROM mcr.microsoft.com/devcontainers/java:21

# Install font libraries and PlantUML dependencies
RUN apt-get update && apt-get install -y \
    libfreetype6 \
    libfontconfig1 \
    libx11-6 \
    libxext6 \
    libxi6 \
    libxrender1 \
    libxtst6 \
    fonts-dejavu-core \
    fontconfig \
    libfreetype6-dev \
    fonts-liberation \
    fonts-noto \
    curl \
    wget \
    && fc-cache -fv \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Set environment variables for headless operation
ENV JAVA_OPTS="-Djava.awt.headless=true -Dfile.encoding=UTF-8"
ENV DISPLAY=:99

# Download PlantUML JAR
RUN wget https://github.com/plantuml/plantuml/releases/download/v1.2024.0/plantuml-1.2024.0.jar -O /usr/local/bin/plantuml.jar

# Create PlantUML wrapper script
RUN echo '#!/bin/bash\njava -jar /usr/local/bin/plantuml.jar "$@"' > /usr/local/bin/plantuml \
    && chmod +x /usr/local/bin/plantuml