.PHONY: generate doctor verify build build-release test lint clean install run-debug run-debug-fast

generate:
	./gradlew generateProtocol

doctor:
	./scripts/check-env.sh

verify:
	./gradlew generateProtocol test lint assembleDebug bundleRelease

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
	./scripts/run-debug.sh

run-debug-fast:
	./scripts/run-debug.sh --skip-build
