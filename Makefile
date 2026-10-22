.PHONY: run stag prod test test-integration coverage clean deps build \
        docker-build docker-push docker-run docker-up docker-down \
        fmt-check fmt-fix init lint outdated help

SHELL          := /bin/bash
.DEFAULT_GOAL  := help

SERVICE_NAME   = be-boilerplate
STAG_PROFILE   = stag
PROD_PROFILE   = prod

# The profile of `make run`. Local development defaults to stag; anything that
# is not a laptop (the image, the orchestrator) sets PROFILE explicitly.
PROFILE       ?= $(STAG_PROFILE)

REGISTRY       = ghcr.io/af2b
IMAGE          = $(REGISTRY)/$(SERVICE_NAME)
VERSION        = $(shell git describe --tags --always --dirty 2>/dev/null || echo "dev")
SHA            = $(shell git rev-parse --short HEAD 2>/dev/null || echo "unknown")
PORT          ?= 8080

# Local configuration (credentials, ports) lives in .env, never in the
# repository. Copy .env.example to .env to start.
-include .env
export

run: ## Run with PROFILE (default: stag)
	@echo "▶ Running with profile $(PROFILE)..."
	@clojure -M:run $(PROFILE)

stag: ## Run with the staging profile
	@$(MAKE) --no-print-directory run PROFILE=$(STAG_PROFILE)

prod: ## Run with the production profile
	@$(MAKE) --no-print-directory run PROFILE=$(PROD_PROFILE)

test: ## Run the unit tests
	@echo "▶ Running unit tests..."
	@clojure -M:test --focus :unit

test-integration: ## Run the integration tests (needs make docker-up)
	@echo "▶ Running integration tests..."
	@clojure -M:test --focus :integration

coverage: ## Run the unit tests with coverage (target/coverage/)
	@echo "▶ Running unit tests with coverage..."
	@clojure -M:coverage

fmt-check: ## Check formatting
	@clojure -M:fmt check src test dev build.clj

fmt-fix: ## Fix formatting
	@clojure -M:fmt fix src test dev build.clj

lint: ## Lint with clj-kondo, failing on any warning
	@mkdir -p .clj-kondo
	@clojure -M:lint --copy-configs --dependencies --skip-lint \
		--lint "$$(clojure -Spath -M:test)" > /dev/null
	@clojure -M:lint --lint src test dev build.clj --fail-level warning

outdated: ## Report dependencies that have a newer version
	@clojure -M:outdated

build: ## Build the uberjar at target/app.jar
	@echo "▶ Building uberjar..."
	@APP_VERSION=$(VERSION) clojure -T:build uber
	@echo "✅ Built: target/app.jar"

clean: ## Remove .cpcache and target
	@rm -rf .cpcache target

deps: ## Resolve and cache every dependency
	@for alias in test coverage lint fmt outdated; do clojure -P -M:$$alias || exit 1; done
	@clojure -P -T:build

docker-build: ## Build the image (tags: sha, version, latest)
	@docker build \
		--build-arg APP_VERSION=$(VERSION) \
		-t $(IMAGE):$(SHA) \
		-t $(IMAGE):$(VERSION) \
		-t $(IMAGE):latest \
		.

docker-push: ## Push every tag of the image
	@docker push $(IMAGE):$(SHA)
	@docker push $(IMAGE):$(VERSION)
	@docker push $(IMAGE):latest

docker-run: ## Run the image with PROFILE and the variables of .env
	@docker run --rm \
		--network host \
		--env-file .env \
		-e PROFILE=$(PROFILE) \
		$(IMAGE):latest

docker-up: ## Start PostgreSQL, Redis and Kafka
	@docker compose up -d --wait

docker-down: ## Stop the infrastructure and remove its volumes
	@docker compose down -v

# ─────────────────────────────────────────────────────────────────────────────
# Scaffolding — Generate a new microservice from this boilerplate
# ─────────────────────────────────────────────────────────────────────────────
#
# Usage:
#   make init NAME=payment-service
#
# This will:
#   1. Copy the boilerplate to ../payment-service
#   2. Rename the directories from be_boilerplate → payment_service
#   3. Replace every reference (be-boilerplate → payment-service)
#   4. Initialise a git repository on main with one commit
# ─────────────────────────────────────────────────────────────────────────────

init: ## Scaffold a new service: make init NAME=payment-service
ifndef NAME
	$(error NAME is required. Usage: make init NAME=payment-service)
endif
	@echo "▶ Scaffolding new service: $(NAME)..."
	$(eval UNDERSCORE := $(subst -,_,$(NAME)))
	$(eval SRC_UNDERSCORE := be_boilerplate)
	$(eval SRC_DASH := be-boilerplate)
	$(eval TARGET_DIR := ../$(NAME))
	@if [ -d "$(TARGET_DIR)" ]; then \
		echo "❌ Directory $(TARGET_DIR) already exists. Aborting."; \
		exit 1; \
	fi
	@mkdir -p "$(TARGET_DIR)"
	@find . -mindepth 1 -maxdepth 1 \
		! -name '.git' \
		! -name '.cpcache' \
		! -name '.lsp' \
		! -name '.env' \
		! -name 'target' \
		! -name 'CHANGELOG.md' \
		-exec cp -r {} "$(TARGET_DIR)/" \;
	@# Keep the lint configuration, not the cache or the imported configs
	@find "$(TARGET_DIR)/.clj-kondo" -mindepth 1 -maxdepth 1 \
		! -name 'config.edn' -exec rm -rf {} + 2>/dev/null || true
	@echo "  ├─ Copied boilerplate to $(TARGET_DIR)"
	@for dir in src test; do \
		if [ -d "$(TARGET_DIR)/$$dir/com/borba/$(SRC_UNDERSCORE)" ]; then \
			mv "$(TARGET_DIR)/$$dir/com/borba/$(SRC_UNDERSCORE)" \
			   "$(TARGET_DIR)/$$dir/com/borba/$(UNDERSCORE)"; \
		fi; \
	done
	@echo "  ├─ Renamed directories"
	@find "$(TARGET_DIR)" -type f \
		\( -name "*.clj" -o -name "*.edn" -o -name "*.md" -o -name "*.sql" \
		   -o -name "Makefile" -o -name "Dockerfile" -o -name ".env.example" \
		   -o -name "*.yml" -o -name "*.yaml" -o -name "*.sh" \) \
		-exec sed -i'' \
			-e "s|$(SRC_UNDERSCORE)|$(UNDERSCORE)|g" \
			-e "s|$(SRC_DASH)|$(NAME)|g" \
			{} +
	@echo "  ├─ Updated every reference"
	@git -C "$(TARGET_DIR)" init -q -b main
	@git -C "$(TARGET_DIR)" add .
	@git -C "$(TARGET_DIR)" commit -q -m "chore: scaffold $(NAME) from be-boilerplate"
	@echo "  └─ Git repository initialised (branch: main)"
	@echo ""
	@echo "✅ Service '$(NAME)' scaffolded at $(TARGET_DIR)"
	@echo ""
	@echo "   cd $(TARGET_DIR)"
	@echo "   cp .env.example .env"
	@echo "   make docker-up && make run"
	@echo ""
	@echo "   To publish it: create the GitHub repository, then"
	@echo "   git remote add origin git@github.com:<owner>/$(NAME).git"
	@echo "   git push -u origin main"

help: ## List the targets
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z_-]+:.*## / {printf "  %-18s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
