# Works on Oracle Cloud Ampere (ARM64) and x86. Build: mvn package && docker build -t zimfete-assets .
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home zimfete
WORKDIR /app
COPY target/asset-financing-*.jar app.jar
USER zimfete
EXPOSE 8090
ENTRYPOINT ["java", "-Xms256m", "-Xmx768m", "-jar", "/app/app.jar"]
