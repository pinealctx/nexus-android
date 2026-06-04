.PHONY: generate build build-release test lint clean install run-debug run-debug-fast

generate:
	./gradlew generateProtocol

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

run-debug:
	powershell -ExecutionPolicy Bypass -File scripts/run-debug.ps1

run-debug-fast:
	powershell -ExecutionPolicy Bypass -File scripts/run-debug.ps1 -SkipBuild
