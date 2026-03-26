#!/bin/bash
# Script to copy all module pom.xml files for Docker builds

MODULES=(
    "lin-spring-cloud-eureka"
    "lin-spring-cloud-config"
    "lin-spring-cloud-gateway"
    "lin-spring-service-user"
    "lin-spring-service-order"
    "lin-spring-service-payment"
    "lin-spring-service-inventory"
    "lin-spring-service-shipping"
)

# Create stub directories with just pom.xml for all modules
for module in "${MODULES[@]}"; do
    mkdir -p "$module"
    cp "$module/pom.xml" "$module/"
done
