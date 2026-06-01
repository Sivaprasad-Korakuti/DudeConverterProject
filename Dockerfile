# Step 1: Build using JDK 26 to match your pom.xml compilation target
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Install JDK 26 manually inside the build container
RUN apt-get update && apt-get install -y wget && \
    wget https://download.oracle.com/java/26/latest/jdk-26_linux-x64_bin.tar.gz && \
    tar -xvf jdk-26_linux-x64_bin.tar.gz && \
    mv jdk-26* /opt/jdk-26

ENV JAVA_HOME=/opt/jdk-26
ENV PATH="$JAVA_HOME/bin:$PATH"

COPY . .
RUN mvn clean package -DskipTests

# Step 2: Run the production application using Java 26
FROM ubuntu:24.04
WORKDIR /app

RUN apt-get update && apt-get install -y wget && \
    wget https://download.oracle.com/java/26/latest/jdk-26_linux-x64_bin.tar.gz && \
    tar -xvf jdk-26_linux-x64_bin.tar.gz && \
    mv jdk-26* /opt/jdk-26 && \
    rm jdk-26_linux-x64_bin.tar.gz

ENV JAVA_HOME=/opt/jdk-26
ENV PATH="$JAVA_HOME/bin:$PATH"

COPY --from=build /app/target/demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]