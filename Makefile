.PHONY: build build-release test lint clean install

build:
	./gradlew assembleDebug

build-release:
	./gradlew assembleRelease

test:
	./gradlew test

lint:
	./gradlew lint

clean:
	./gradlew clean

install:
	./gradlew installDebug
