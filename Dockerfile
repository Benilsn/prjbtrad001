FROM quay.io/quarkus/quarkus-distroless-image:2.16.6.Final

COPY target/quarkus-app /work/
WORKDIR /work
EXPOSE 8080

CMD ["java", "-jar", "quarkus-run.jar"]
