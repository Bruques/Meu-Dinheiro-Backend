# ESTÁGIO 1: O "Construtor" (Baixa o Maven e compila o seu código)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# ESTÁGIO 2: O "Servidor Leve" (Pega só o arquivo .jar e roda)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/meudinheiro-0.0.1-SNAPSHOT.jar app.jar

# A nossa trava de memória de 256MB vai embutida aqui!
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]