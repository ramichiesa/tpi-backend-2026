# Construccion en dos etapas. El contexto de build es la RAIZ del repositorio (ver docker-compose.yml).
# Etapa 1: instala el POM padre y el JAR comun en el repositorio Maven local (.m2) de la imagen y
# luego compila el servicio, que resuelve tpi-common como dependencia Maven (mecanismo minimo
# del enunciado), igual que en una maquina de desarrollo. No depende de ningun JAR del host.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -q -N install  && mvn -B -q -f tpi-common/pom.xml install -DskipTests  && mvn -B -q -f ms-donaciones/pom.xml package -DskipTests

# Etapa 2: imagen liviana solo con el JRE y el JAR ejecutable del servicio.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/ms-donaciones/target/app.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
