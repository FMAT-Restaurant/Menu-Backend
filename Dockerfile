# 1. Etapa de compilación: JDK Eclipse Temurin 25.0.4.1 (versión definida en el stack)
FROM eclipse-temurin:25.0.4.1_1-jdk-noble AS build

# 2. Directorio de trabajo dentro del contenedor
WORKDIR /workspace

# 3. Copiar primero el wrapper y los scripts de Gradle
# (Buenas prácticas: aprovecha la caché de capas si las dependencias no cambian)
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
RUN ./gradlew dependencies --no-daemon -q > /dev/null

# 4. Copiar el código fuente y generar el JAR ejecutable de Spring Boot (las pruebas se ejecutan en CI)
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -q \
	&& find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

# 5. Etapa de ejecución: solo el JRE, sin herramientas de compilación
FROM eclipse-temurin:25.0.4.1_1-jre-noble

WORKDIR /app

# 6. Ejecutar como usuario sin privilegios
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

COPY --from=build /workspace/app.jar app.jar

# 7. Puerto HTTP por defecto de Spring Boot
EXPOSE 8080

# 8. Comando por defecto al ejecutar el contenedor
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
