######################################################################
# Nombre: Dockerfile
# Descripción: Imagen del backend (multi-stage). Contexto de construcción: raíz del repositorio (necesita ../database/migrations).
# Fecha de creación: 2026-10-07
# Fecha de actualización: 2026-10-07
# Autor: Equipo Ferretería
# Versión: 1.0.0
######################################################################
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml backend/pom.xml
COPY database database
COPY backend/src backend/src
RUN cd backend && mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app
WORKDIR /app
COPY --from=build /src/backend/target/inventario-backend-1.0.0.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
