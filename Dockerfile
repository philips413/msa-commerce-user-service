FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
# 의존성 레이어를 먼저 받아 두면 소스만 바뀐 빌드는 캐시를 탄다
COPY settings.gradle build.gradle ./
RUN gradle dependencies --no-daemon > /dev/null
COPY src src
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:21.0.8_9-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER 10001
EXPOSE 8080
# 컨테이너 메모리 limit 기준으로 힙을 잡는다 (나머지는 메타스페이스·스레드 등 힙 밖 메모리 몫)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]
