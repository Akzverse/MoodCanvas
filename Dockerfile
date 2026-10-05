# Stage 1: Build WAR with Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy POM and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and package WAR
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Deploy to Apache Tomcat 10.1
FROM tomcat:10.1-jdk17-temurin

# Clean default webapps
RUN rm -rf /usr/local/tomcat/webapps/*

# Deploy MoodCanvas as ROOT.war (accessible at /) and moodcanvas.war (accessible at /moodcanvas/)
COPY --from=build /workspace/target/moodcanvas.war /usr/local/tomcat/webapps/ROOT.war
COPY --from=build /workspace/target/moodcanvas.war /usr/local/tomcat/webapps/moodcanvas.war

EXPOSE 8080

CMD ["catalina.sh", "run"]
