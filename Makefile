# Simple MP3 — developer shortcuts
# Usage: make <target>   or   make help

.DEFAULT_GOAL := help

ANDROID_DIR ?= android
IOS_DIR     ?= ios
GRADLE   ?= $(ANDROID_DIR)/gradlew -p $(ANDROID_DIR)
ADB      ?= adb
MODULE   ?= :app

# Optional Kotlin formatter (auto-downloaded on first use)
KTLINT_VERSION ?= 1.5.0
TOOLS_DIR      := .tools
KTLINT         := $(TOOLS_DIR)/ktlint

# Gradle can be chatty; pass extra flags via GRADLE_OPTS or ARGS
#   make test ARGS="--info"
ARGS ?=

.PHONY: help
help: ## Show this help
	@echo "Simple MP3 — make targets"
	@echo
	@grep -E '^[a-zA-Z0-9_-]+:.*?## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'
	@echo
	@echo "Examples:"
	@echo "  make build          # debug APK"
	@echo "  make test lint      # unit tests + Android lint"
	@echo "  make install run    # install debug and launch"
	@echo "  make format         # ktlint format (downloads ktlint once)"
	@echo "  make screenshots-ios # App Store: sim capture + marketing frames"
	@echo "  make screenshots-android # Play Store: HTML → PNGs"

# ── Build ──────────────────────────────────────────────────────────

.PHONY: build
build: ## Assemble debug APK
	$(GRADLE) $(MODULE):assembleDebug $(ARGS)

.PHONY: debug
debug: build ## Alias for build

.PHONY: release
release: ## Assemble release APK (unsigned unless signing is configured)
	$(GRADLE) $(MODULE):assembleRelease $(ARGS)

.PHONY: aab
aab: ## Assemble release App Bundle (.aab)
	$(GRADLE) $(MODULE):bundleRelease $(ARGS)

.PHONY: bundle
bundle: aab ## Alias for aab

.PHONY: compile
compile: ## Compile debug Kotlin/Java only (fast feedback)
	$(GRADLE) $(MODULE):compileDebugKotlin $(ARGS)

.PHONY: assemble
assemble: ## Assemble all variants
	$(GRADLE) assemble $(ARGS)

# ── Test ───────────────────────────────────────────────────────────

.PHONY: test
test: ## Run JVM unit tests (debug)
	$(GRADLE) $(MODULE):testDebugUnitTest $(ARGS)

.PHONY: test-unit
test-unit: test ## Alias for test

.PHONY: test-release
test-release: ## Run JVM unit tests (release)
	$(GRADLE) $(MODULE):testReleaseUnitTest $(ARGS)

.PHONY: test-all
test-all: ## Run unit tests for all variants
	$(GRADLE) test $(ARGS)

.PHONY: test-android
test-android: ## Run instrumentation tests on a connected device/emulator
	$(GRADLE) $(MODULE):connectedDebugAndroidTest $(ARGS)

.PHONY: test-report
test-report: test ## Run unit tests and print report path
	@echo "Unit test report: $(ANDROID_DIR)/app/build/reports/tests/testDebugUnitTest/index.html"

# ── Lint & format ──────────────────────────────────────────────────

.PHONY: lint
lint: ## Run Android Lint (debug)
	$(GRADLE) $(MODULE):lintDebug $(ARGS)

.PHONY: lint-release
lint-release: ## Run Android Lint (release) + vital checks
	$(GRADLE) $(MODULE):lintRelease $(MODULE):lintVitalRelease $(ARGS)

.PHONY: lint-fix
lint-fix: ## Apply safe Android Lint auto-fixes
	$(GRADLE) $(MODULE):lintFix $(ARGS)

.PHONY: lint-report
lint-report: lint ## Run lint and print HTML report path
	@echo "Lint report: $(ANDROID_DIR)/app/build/reports/lint-results-debug.html"

.PHONY: format
format: $(KTLINT) ## Format Kotlin sources with ktlint
	$(KTLINT) -F "$(ANDROID_DIR)/app/src/**/*.kt" "$(ANDROID_DIR)/app/src/**/*.kts" "$(ANDROID_DIR)/*.kts" "$(ANDROID_DIR)/build.gradle.kts" "$(ANDROID_DIR)/settings.gradle.kts"

.PHONY: format-check
format-check: $(KTLINT) ## Check Kotlin formatting (no write)
	$(KTLINT) "$(ANDROID_DIR)/app/src/**/*.kt" "$(ANDROID_DIR)/app/src/**/*.kts" "$(ANDROID_DIR)/*.kts" "$(ANDROID_DIR)/build.gradle.kts" "$(ANDROID_DIR)/settings.gradle.kts"

$(KTLINT):
	@mkdir -p $(TOOLS_DIR)
	@echo "Downloading ktlint $(KTLINT_VERSION)…"
	@curl -fsSL -o $(KTLINT) \
		"https://github.com/pinterest/ktlint/releases/download/$(KTLINT_VERSION)/ktlint"
	@chmod +x $(KTLINT)
	@echo "Installed $(KTLINT)"

# ── Quality gate ───────────────────────────────────────────────────

.PHONY: check
check: ## Lint + unit tests (CI-friendly)
	$(GRADLE) $(MODULE):lintDebug $(MODULE):testDebugUnitTest $(ARGS)

.PHONY: verify
verify: format-check check ## Format check + lint + unit tests

.PHONY: ci
ci: clean verify build ## Full clean CI: format-check, lint, test, debug APK

# ── Device ─────────────────────────────────────────────────────────

.PHONY: install
install: ## Install debug APK on a connected device
	$(GRADLE) $(MODULE):installDebug $(ARGS)

.PHONY: uninstall
uninstall: ## Uninstall the app from connected devices
	$(GRADLE) $(MODULE):uninstallAll $(ARGS)

.PHONY: run
run: ## Launch the app (install first if needed)
	@$(ADB) shell am start -n io.karpilabs.simplemp3/.MainActivity

.PHONY: install-run
install-run: install run ## Install debug APK and launch

.PHONY: logcat
logcat: ## Tail logcat filtered to Simple MP3
	$(ADB) logcat --pid=$$($(ADB) shell pidof -s io.karpilabs.simplemp3) 2>/dev/null || \
		$(ADB) logcat | grep -iE 'simplemp3|karpilabs|AndroidRuntime'

.PHONY: devices
devices: ## List connected adb devices
	$(ADB) devices -l

# ── Maintenance ────────────────────────────────────────────────────

.PHONY: clean
clean: ## Delete build outputs
	$(GRADLE) clean $(ARGS)

.PHONY: clean-all
clean-all: clean ## Clean build outputs and local tool cache
	rm -rf $(TOOLS_DIR)

.PHONY: deps
deps: ## Show app dependency tree (debug runtime)
	$(GRADLE) $(MODULE):dependencies --configuration debugRuntimeClasspath $(ARGS)

.PHONY: tasks
tasks: ## List Gradle tasks
	$(GRADLE) tasks $(ARGS)

.PHONY: wrapper
wrapper: ## Refresh Gradle wrapper (requires local Gradle)
	gradle wrapper --gradle-version 8.13

.PHONY: signing-report
signing-report: ## Print signing config / SHA fingerprints
	$(GRADLE) $(MODULE):signingReport $(ARGS)

# ── iOS ────────────────────────────────────────────────────────────
# Requires Xcode + xcodebuild on macOS.

IOS_PROJECT     ?= $(IOS_DIR)/Simple MP3.xcodeproj
IOS_SCHEME      ?= Simple MP3
IOS_DESTINATION ?= generic/platform=iOS Simulator
# Tests need a concrete (bootable) simulator, not a generic destination;
# pick the first available iPhone unless the caller overrides IOS_SIMULATOR.
IOS_SIMULATOR        ?= $(shell xcrun simctl list devices available -j | grep -o '"name" : "iPhone[^"]*"' | head -1 | sed 's/"name" : "//;s/"$$//')
IOS_TEST_DESTINATION ?= platform=iOS Simulator,name=$(IOS_SIMULATOR)

.PHONY: ios-build
ios-build: ## Build the iOS app for the simulator
	xcodebuild -project "$(IOS_PROJECT)" -scheme "$(IOS_SCHEME)" -destination "$(IOS_DESTINATION)" build

.PHONY: ios-test
ios-test: ## Run iOS unit/UI tests
	xcodebuild -project "$(IOS_PROJECT)" -scheme "$(IOS_SCHEME)" -destination "$(IOS_TEST_DESTINATION)" test

.PHONY: ios-run
ios-run: ## Build and launch the iOS app in the Simulator
	xcodebuild -project "$(IOS_PROJECT)" -scheme "$(IOS_SCHEME)" -destination "$(IOS_DESTINATION)" build
	open -a Simulator

# ── Store screenshots ──────────────────────────────────────────────
# Re-runnable marketing asset pipelines. See store-listing/ and store-listing/ios/.

STORE_DIR     ?= store-listing
IOS_STORE_DIR ?= $(STORE_DIR)/ios

.PHONY: screenshots-android
screenshots-android: ## Play Store: re-render icons, feature graphic, phone screenshots (Chrome)
	@chmod +x "$(STORE_DIR)/render.sh"
	"$(STORE_DIR)/render.sh"

.PHONY: screenshots-ios-capture
screenshots-ios-capture: ## App Store: build + Simulator capture (iPhone + iPad, needs Xcode, free disk)
	@chmod +x "$(IOS_STORE_DIR)/capture-screenshots.sh"
	"$(IOS_STORE_DIR)/capture-screenshots.sh"

.PHONY: screenshots-ios-render
screenshots-ios-render: ## App Store: marketing frames + resize to 1284×2778 / 1242×2688 (Chrome)
	@chmod +x "$(IOS_STORE_DIR)/render.sh"
	"$(IOS_STORE_DIR)/render.sh"

.PHONY: screenshots-ios
screenshots-ios: screenshots-ios-capture screenshots-ios-render ## App Store: capture then render (full pipeline)

.PHONY: screenshots
screenshots: screenshots-android screenshots-ios ## All store screenshots (Android HTML + iOS sim)
