FROM maven:3.8.4-openjdk-17-slim AS MAVEN_BUILD

WORKDIR /build/

COPY pom.xml mvnw mvnw.cmd /build/

RUN mvn -N io.takari:maven:wrapper

COPY settings.xml /root/.m2

COPY src /build/src/

RUN mvn package -Dmaven.test.skip=true

FROM eclipse-temurin:17.0.1_12-jre-alpine

WORKDIR /app

COPY --from=MAVEN_BUILD /build/target/generator-0.0.1-SNAPSHOT.jar /app/

RUN apk update && apk add curl && \
    curl https://raw.githubusercontent.com/findy-network/findy-agent-cli/HEAD/install.sh > install.sh && \
    chmod a+x install.sh && \
    ./install.sh -b /bin

EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.profiles.active=docker","-jar", "generator-0.0.1-SNAPSHOT.jar"]
