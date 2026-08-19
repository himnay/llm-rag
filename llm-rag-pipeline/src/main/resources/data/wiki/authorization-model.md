# <span style="color:hsl(117,80%,58%)">Authorization Model at NexaCorp</span>

## <span style="color:hsl(255,80%,58%)">Overview</span>

Authorization at NexaCorp determines what authenticated users are allowed to access
within internal systems. Authorization decisions are based on roles, departments,
and business context.

## <span style="color:hsl(32,80%,58%)">Role-Based Access Control (RBAC)</span>

NexaCorp uses RBAC as the primary authorization mechanism. Common roles include:

- Engineering
- HR
- Support
- Operations
- Security

Each role has predefined access permissions to applications, data, and tools.

## <span style="color:hsl(170,80%,58%)">Department-Based Constraints</span>

In addition to roles, access may be constrained by department. For example:

- HR users can access employee records and HR policies
- Engineering users can access technical documentation and source repositories
- Support users can access customer-facing knowledge bases

## <span style="color:hsl(307,80%,58%)">Privileged Access</span>

Certain operations require elevated privileges:

- Access to production systems
- Viewing confidential security reports
- Managing user access and roles

Privileged access requires additional approval and is audited regularly.

## <span style="color:hsl(85,80%,58%)">Authorization Tokens</span>

Authorization information is embedded in access tokens issued during authentication.
Tokens typically include:

- User role
- Department
- Access scopes

## <span style="color:hsl(222,80%,58%)">Common Authorization Issues</span>

- Access denied due to missing role
- Stale permissions after role change
- Insufficient privileges for sensitive operations

## <span style="color:hsl(360,80%,58%)">Related Documents</span>

Refer to the IT Access Control Policy for detailed access request and review processes.
