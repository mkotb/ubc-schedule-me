FROM openjdk:11.0-slim

WORKDIR /server

COPY ./build/libs/ubc-schedule-me.jar ubc-schedule-me.jar
CMD java -jar ubc-schedule-me.jar
