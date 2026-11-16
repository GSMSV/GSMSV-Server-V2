# 빌드: JDK에서 bootJar만 만든다 (테스트는 CI에서 돈다)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 내려받기를 따로 캐시한다. 소스가 바뀌어도 이 층은 다시 만들지 않는다
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -n 1)" /workspace/app.jar

# 실행: JRE만 담아 이미지 크기를 줄인다. 비밀값은 이미지에 넣지 않고 실행할 때 환경 변수로 받는다
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home gsmsv
WORKDIR /app
COPY --from=build --chown=gsmsv:gsmsv /workspace/app.jar app.jar
USER gsmsv
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
