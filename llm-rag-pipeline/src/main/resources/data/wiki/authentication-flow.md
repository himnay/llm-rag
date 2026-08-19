# <span style="color:hsl(224,68%,44%)">Authentication Flow at NexaCorp</span>

## <span style="color:hsl(275,68%,44%)">Overview</span>

NexaCorp uses a centralized authentication system to manage user identity and access
across all internal and customer-facing applications.

## <span style="color:hsl(327,68%,44%)">Authentication Mechanism</span>

- Users authenticate using company-issued credentials
- Single Sign-On (SSO) is enabled across internal systems
- Multi-Factor Authentication (MFA) is mandatory for sensitive operations

## <span style="color:hsl(18,68%,44%)">Login Flow</span>

1. User accesses an internal application
2. Request is redirected to the Authentication Service
3. Credentials are validated
4. MFA challenge is performed if required
5. Access token is issued upon successful authentication

## <span style="color:hsl(70,68%,32%)">Token Management</span>

- Access tokens are short-lived
- Refresh tokens are securely stored
- Tokens include user role and department metadata

## <span style="color:hsl(121,68%,32%)">Common Issues</span>

- Expired tokens causing access failures
- MFA challenges not completed
- Clock skew between services

## <span style="color:hsl(173,68%,36%)">Related Policies</span>

Refer to the Information Security Policy for password and MFA requirements.
