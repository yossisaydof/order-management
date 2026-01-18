#!/bin/bash
# ============================================
# API Test Script
# ============================================
# Runs API tests against the running application.
# Usage: ./scripts/api_test.sh [test-name]
#   test-name: health, orders, errors, or all (default)
# ============================================

set -e

# --- Configuration ---
BASE_URL="${BASE_URL:-http://localhost:8080}"
STORE_ID="${STORE_ID:-demo-store}"

# --- Colors ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# --- JSON formatting (jq if available, otherwise cat) ---
format_json() {
    if command -v jq &> /dev/null; then
        jq .
    else
        cat
    fi
}

# --- Helper functions ---
print_header() {
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""
}

print_test() {
    echo -e "${CYAN}[$1] $2${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# --- Test: Health ---
test_health() {
    print_header "API Test: Health"

    print_test "1" "Health Check"
    echo "GET ${BASE_URL}/actuator/health"

    RESPONSE=$(curl -s "${BASE_URL}/actuator/health")
    echo "$RESPONSE" | format_json

    if echo "$RESPONSE" | grep -q '"status"'; then
        print_success "Health endpoint responded"
    else
        print_error "Health endpoint failed"
        return 1
    fi
    echo ""
}

# --- Test: Orders (CRUD) ---
test_orders() {
    print_header "API Test: Orders"

    # Create Order
    print_test "1" "Create Order"
    echo "POST ${BASE_URL}/${STORE_ID}/orders"

    ORDER_RESPONSE=$(curl -s -X POST "${BASE_URL}/${STORE_ID}/orders" \
        -H "Content-Type: application/json" \
        -d '{
            "email": "test@example.com",
            "firstName": "John",
            "lastName": "Doe",
            "orderDate": "2026-01-15T10:00:00Z",
            "lineItems": [{
                "externalProductId": "PROD-001",
                "productName": "Test Product",
                "productPrice": 99.99,
                "quantity": 2
            }]
        }')

    echo "$ORDER_RESPONSE" | format_json

    # Extract order ID
    if command -v jq &> /dev/null; then
        ORDER_ID=$(echo "$ORDER_RESPONSE" | jq -r '.orderId // empty')
    else
        ORDER_ID=$(echo "$ORDER_RESPONSE" | sed -n 's/.*"orderId":"\([^"]*\)".*/\1/p')
    fi

    if [ -z "$ORDER_ID" ]; then
        print_error "Failed to create order"
        return 1
    fi

    print_success "Order created: $ORDER_ID"
    echo ""

    # Wait for async processing
    echo -e "${YELLOW}Waiting for async processing...${NC}"
    sleep 3
    echo ""

    # Get Order
    print_test "2" "Get Order"
    echo "GET ${BASE_URL}/${STORE_ID}/orders/${ORDER_ID}"

    GET_RESPONSE=$(curl -s "${BASE_URL}/${STORE_ID}/orders/${ORDER_ID}")
    echo "$GET_RESPONSE" | format_json

    if echo "$GET_RESPONSE" | grep -q '"storeId"'; then
        print_success "Order retrieved successfully"
    else
        print_error "Failed to retrieve order"
        return 1
    fi
    echo ""

    # List Orders
    print_test "3" "List Orders"
    echo "GET ${BASE_URL}/${STORE_ID}/orders?page=0&size=5"

    LIST_RESPONSE=$(curl -s "${BASE_URL}/${STORE_ID}/orders?page=0&size=5")
    echo "$LIST_RESPONSE" | format_json

    if echo "$LIST_RESPONSE" | grep -q '"orders"'; then
        print_success "Orders listed successfully"
    else
        print_error "Failed to list orders"
        return 1
    fi
    echo ""
}

# --- Test: Error Handling ---
test_errors() {
    print_header "API Test: Error Handling"

    # Validation Error
    print_test "1" "Validation Error"
    echo "POST ${BASE_URL}/${STORE_ID}/orders (invalid data)"

    VALIDATION_RESPONSE=$(curl -s -X POST "${BASE_URL}/${STORE_ID}/orders" \
        -H "Content-Type: application/json" \
        -d '{"email": "invalid", "lineItems": []}')

    echo "$VALIDATION_RESPONSE" | format_json

    if echo "$VALIDATION_RESPONSE" | grep -qi 'error\|invalid\|validation'; then
        print_success "Validation error returned correctly"
    else
        print_error "Expected validation error"
    fi
    echo ""

    # Not Found Error
    print_test "2" "Not Found Error"
    echo "GET ${BASE_URL}/${STORE_ID}/orders/00000000-0000-0000-0000-000000000000"

    NOT_FOUND_RESPONSE=$(curl -s "${BASE_URL}/${STORE_ID}/orders/00000000-0000-0000-0000-000000000000")
    echo "$NOT_FOUND_RESPONSE" | format_json

    if echo "$NOT_FOUND_RESPONSE" | grep -qi 'not found\|error'; then
        print_success "Not found error returned correctly"
    else
        print_error "Expected not found error"
    fi
    echo ""
}

# --- Main ---
main() {
    local test_name="${1:-all}"

    case "$test_name" in
        health)
            test_health
            ;;
        orders)
            test_orders
            ;;
        errors)
            test_errors
            ;;
        all)
            test_health
            test_orders
            test_errors
            echo -e "${GREEN}============================================${NC}"
            echo -e "${GREEN}  All API Tests Complete!${NC}"
            echo -e "${GREEN}============================================${NC}"
            echo ""
            echo -e "Swagger UI: ${BLUE}${BASE_URL}/swagger-ui.html${NC}"
            echo ""
            ;;
        *)
            echo "Usage: $0 [health|orders|errors|all]"
            exit 1
            ;;
    esac
}

main "$@"
