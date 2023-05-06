FROM amazoncorretto:17

COPY entrypoint.sh /entrypoint.sh
COPY build/libs/fileService-*-all.jar /app/app.jar

ENTRYPOINT ["/bin/sh", "entrypoint.sh"]