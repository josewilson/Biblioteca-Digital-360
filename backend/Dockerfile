# Backend da Biblioteca Digital 360. Build em duas etapas: o JDK/Maven fica só na etapa de build.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Dependências primeiro: a camada só é refeita quando o pom.xml muda.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
# Os testes rodam no CI (precisam de Docker); aqui só empacota.
RUN mvn -B -q -DskipTests package && cp target/biblioteca-digital-360-*.jar /build/app.jar

FROM eclipse-temurin:25-jre-alpine
# Não roda como root.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER app
EXPOSE 8080
# Usa ~75% da memória do container em vez do padrão da JVM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
