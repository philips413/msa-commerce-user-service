IMAGE  := devbook/user-service
TAG    := dev
GRADLE := docker run --rm -v "$(PWD)":/workspace -w /workspace gradle:8.14.3-jdk21 gradle --no-daemon

.PHONY: test image wrapper

## 로컬 JDK 없이 Docker 안에서 테스트
test:
	$(GRADLE) test

image:
	docker build -t $(IMAGE):$(TAG) .

## Gradle Wrapper 생성 (최초 1회, 생성된 gradlew·gradle/ 는 커밋한다)
wrapper:
	$(GRADLE) wrapper --gradle-version 8.14.3
