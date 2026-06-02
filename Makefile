.PHONY: build build-release test lint clean install sync-core-libs run-debug run-debug-fast

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

sync-core-libs:
	cd ../nexus-core && powershell -ExecutionPolicy Bypass -File scripts/build-android-libs.ps1

run-debug:
	powershell -ExecutionPolicy Bypass -File scripts/run-debug.ps1

run-debug-fast:
	powershell -ExecutionPolicy Bypass -File scripts/run-debug.ps1 -SkipBuild
