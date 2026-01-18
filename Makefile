# ============================================
# Yotpo Order Management System - Makefile
# ============================================

# --- Configuration ---
DOCKER_COMPOSE = docker compose -f docker/docker-compose.yml
MVN            = ./mvnw
BASE_URL       = http://localhost:8080

# Container names
POSTGRES_CONTAINER = yotpo-postgres
KAFKA_CONTAINER    = yotpo-kafka

# Database credentials
DB_USER = yossi
DB_NAME = yotpo_orders

# --- Colors ---
RED    = \033[0;31m
GREEN  = \033[0;32m
YELLOW = \033[1;33m
BLUE   = \033[0;34m
NC     = \033[0m

# --- Reusable Macros ---
define print_header
	@echo "$(BLUE)============================================$(NC)"
	@echo "$(BLUE)  $(1)$(NC)"
	@echo "$(BLUE)============================================$(NC)"
	@echo ""
endef

define check_docker
	@if ! docker info > /dev/null 2>&1; then \
		echo "$(RED)Error: Docker is not running. Please start Docker Desktop.$(NC)"; \
		exit 1; \
	fi
endef

define wait_for_postgres
	@echo "PostgreSQL: "
	@until docker exec $(POSTGRES_CONTAINER) pg_isready -U $(DB_USER) -d $(DB_NAME) > /dev/null 2>&1; do \
		echo "."; \
		sleep 2; \
	done
	@echo " $(GREEN)Ready$(NC)"
endef

define wait_for_kafka
	@echo "Kafka: "
	@until docker exec $(KAFKA_CONTAINER) kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1; do \
		echo "."; \
		sleep 2; \
	done
	@echo " $(GREEN)Ready$(NC)"
endef

define wait_for_app
	@echo "Application: "
	@until curl -s $(BASE_URL)/actuator/health > /dev/null 2>&1; do \
		echo "."; \
		sleep 2; \
	done
	@echo " $(GREEN)Ready$(NC)"
endef

define print_urls
	@echo ""
	@echo "$(GREEN)System is ready!$(NC)"
	@echo ""
	@echo "Application:  $(BLUE)$(BASE_URL)$(NC)"
	@echo "Swagger UI:   $(BLUE)$(BASE_URL)/swagger-ui.html$(NC)"
	@echo "Health Check: $(BLUE)$(BASE_URL)/actuator/health$(NC)"
	@echo ""
endef

# --- Targets ---
.PHONY: help run stop stop-clean test test-unit test-integration test-api logs status

# ============================================
# Help
# ============================================

help: ## Show available commands
	$(call print_header,Yotpo Order Management System)
	@echo "$(YELLOW)Usage:$(NC) make <target>"
	@echo ""
	@echo "$(YELLOW)Run:$(NC)"
	@echo "  $(GREEN)run$(NC)          Start full system (Docker: app + infra)"
	@echo "  $(GREEN)run-infra$(NC)    Start only infrastructure services (PostgreSQL + Kafka)"
	@echo ""
	@echo "$(YELLOW)Stop:$(NC)"
	@echo "  $(GREEN)stop$(NC)         Stop all services (preserve data)"
	@echo "  $(GREEN)stop-clean$(NC)   Stop all services and remove data"
	@echo ""
	@echo "$(YELLOW)Test:$(NC)"
	@echo "  $(GREEN)test$(NC)         Run all tests"
	@echo "  $(GREEN)test-unit$(NC)    Run unit tests only"
	@echo "  $(GREEN)test-integration$(NC)  Run integration tests"
	@echo "  $(GREEN)test-api$(NC)     Run API tests (requires running app)"
	@echo ""
	@echo "$(YELLOW)Other:$(NC)"
	@echo "  $(GREEN)logs$(NC)         Show logs (follow mode)"
	@echo "  $(GREEN)status$(NC)       Check status of all services"
	@echo ""

# ============================================
# Run
# ============================================

run: ## Start full system using Docker Compose (app + infra)
	$(call print_header,Starting Full System (Docker))
	$(call check_docker)
	@echo "$(YELLOW)Starting all services...$(NC)"
	@$(DOCKER_COMPOSE) up -d --build
	@echo ""
	@echo "$(YELLOW)Waiting for services to be ready...$(NC)"
	$(call wait_for_postgres)
	$(call wait_for_kafka)
	$(call wait_for_app)
	$(call print_urls)

run-infra: ## Start only infrastructure services (PostgreSQL + Kafka)
	$(call print_header,Starting Infrastructure Services)
	$(call check_docker)
	@echo "$(YELLOW)Starting infrastructure services...$(NC)"
	@$(DOCKER_COMPOSE) up -d postgres kafka
	@echo ""
	@echo "$(YELLOW)Waiting for infrastructure to be ready...$(NC)"
	$(call wait_for_postgres)
	$(call wait_for_kafka)
	@echo "$(GREEN)Infrastructure services are ready!$(NC)"
	@echo ""

# ============================================
# Stop
# ============================================

stop: ## Stop all services (preserve data volumes)
	$(call print_header,Stopping Services)
	@echo "$(YELLOW)Stopping services (preserving data)...$(NC)"
	@$(DOCKER_COMPOSE) down
	@echo ""
	@echo "$(GREEN)Services stopped. Data volumes preserved.$(NC)"
	@echo "Use $(YELLOW)make stop-clean$(NC) to also remove data."
	@echo ""

stop-clean: ## Stop all services and remove volumes
	$(call print_header,Stopping Services (Clean))
	@echo "$(RED)WARNING: This will delete all data!$(NC)"
	@echo "$(YELLOW)Stopping services and removing volumes...$(NC)"
	@$(DOCKER_COMPOSE) down -v
	@echo ""
	@echo "$(GREEN)Services stopped and volumes removed.$(NC)"
	@echo ""

# ============================================
# Test
# ============================================

test: test-unit test-integration test-api ## Run all tests

test-unit: ## Run unit tests only
	$(call print_header,Running Unit Tests)
	$(MVN) test -Dtest="OrderServiceTest,OrderControllerTest"

test-integration: ## Run integration tests (starts infra automatically)
	$(call print_header,Running Integration Tests)
	$(call check_docker)
	@echo "$(YELLOW)Starting infrastructure for tests...$(NC)"
	@$(DOCKER_COMPOSE) up -d postgres kafka
	@echo ""
	$(call wait_for_postgres)
	$(call wait_for_kafka)
	@echo ""
	$(MVN) test -Dtest="OrderIntegrationTest"

test-api: ## Run API tests (requires running application)
	$(call print_header,Running API Tests)
	@if ! curl -s $(BASE_URL)/actuator/health > /dev/null 2>&1; then \
		echo "$(RED)Error: Application is not running.$(NC)"; \
		echo "Run $(YELLOW)make run$(NC) first."; \
		exit 1; \
	fi
	@echo "$(GREEN)Application is running$(NC)"
	@echo ""
	@./scripts/api_test.sh

# ============================================
# Logs & Status
# ============================================

logs: ## Show all logs (follow mode)
	@$(DOCKER_COMPOSE) logs -f

status: ## Check status of all services
	$(call print_header,Service Status)
	@echo "$(YELLOW)Docker Containers:$(NC)"
	@$(DOCKER_COMPOSE) ps
	@echo ""
	@echo "$(YELLOW)Service Health:$(NC)"
	@echo ""
	@echo "PostgreSQL:  "
	@if docker exec $(POSTGRES_CONTAINER) pg_isready -U $(DB_USER) -d $(DB_NAME) > /dev/null 2>&1; then \
		echo "$(GREEN)Healthy$(NC)"; \
	else \
		echo "$(RED)Not Ready$(NC)"; \
	fi
	@echo "Kafka:       "
	@if docker exec $(KAFKA_CONTAINER) kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1; then \
		echo "$(GREEN)Healthy$(NC)"; \
	else \
		echo "$(RED)Not Ready$(NC)"; \
	fi
	@echo "Application: "
	@if curl -s $(BASE_URL)/actuator/health > /dev/null 2>&1; then \
		echo "$(GREEN)Healthy$(NC)"; \
	else \
		echo "$(RED)Not Running$(NC)"; \
	fi
	@echo ""
	@echo "$(YELLOW)URLs:$(NC)"
	@echo "Application:  $(BLUE)$(BASE_URL)$(NC)"
	@echo "Swagger UI:   $(BLUE)$(BASE_URL)/swagger-ui.html$(NC)"
	@echo "Health Check: $(BLUE)$(BASE_URL)/actuator/health$(NC)"
	@echo ""

