# Contexto de build: raiz do repositório (docker compose define context: ..)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY common/pom.xml common/
COPY api/pom.xml api/
COPY sessao/pom.xml sessao/
COPY ponte/pom.xml ponte/
# A ponte compila contra o servidor de sessão nos testes (dependência de teste), por isso ele entra no build
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl ponte -am dependency:go-offline
COPY common common
COPY sessao sessao
COPY ponte ponte
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl ponte -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app
USER app
WORKDIR /app
COPY --from=build /src/ponte/target/ponte.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
# Porta do WebSocket da ponte (argumento [PORTA] do main)
CMD ["8082"]
