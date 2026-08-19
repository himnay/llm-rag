# <span style="color:hsl(81,80%,58%)">Deployment Architecture at NexaCorp</span>

## <span style="color:hsl(219,80%,58%)">Overview</span>

NexaCorp deploys its applications using a cloud-native architecture designed for scalability,
resilience, and security. All core services are containerized and managed centrally.

## <span style="color:hsl(356,80%,58%)">Infrastructure Model</span>

- Applications run as Docker containers
- Containers are orchestrated using Kubernetes
- Separate environments exist for Dev, QA, and Production

## <span style="color:hsl(134,80%,58%)">Service Deployment</span>

Each service is deployed independently and can be scaled based on demand.
Stateless services are preferred wherever possible to simplify scaling.

## <span style="color:hsl(271,80%,58%)">Configuration Management</span>

- Configuration is externalized using environment variables and config files
- Secrets are managed using a secure secrets manager
- No sensitive data is stored directly in application code

## <span style="color:hsl(49,80%,50%)">Networking & Security</span>

- Services communicate over internal networks
- External access is routed through an API Gateway
- Network policies restrict unauthorized service communication

## <span style="color:hsl(186,80%,58%)">Observability</span>

- Centralized logging is enabled for all services
- Metrics are collected for latency, error rates, and throughput
- Alerts are configured for critical system failures

## <span style="color:hsl(324,80%,58%)">Related Documentation</span>

Refer to the API Guidelines and Information Security Policy for additional operational standards.
