# <span style="color:hsl(117,68%,32%)">Authorization Model at NexaCorp</span>

## <span style="color:hsl(162,68%,36%)">Overview</span>

Authorization at NexaCorp determines what authenticated users are allowed to access
within internal systems. Authorization decisions are based on roles, departments,
and business context.

## <span style="color:hsl(207,68%,44%)">Role-Based Access Control (RBAC)</span>

NexaCorp uses RBAC as the primary authorization mechanism. Common roles include:

- Engineering
- HR
- Support
- Operations
- Security

Each role has predefined access permissions to applications, data, and tools.

## <span style="color:hsl(252,68%,44%)">Department-Based Constraints</span>

In addition to roles, access may be constrained by department. For example:

- HR users can access employee records and HR policies
- Engineering users can access technical documentation and source repositories
- Support users can access customer-facing knowledge bases

## <span style="color:hsl(297,68%,44%)">Privileged Access</span>

Certain operations require elevated privileges:

- Access to production systems
- Viewing confidential security reports
- Managing user access and roles

Privileged access requires additional approval and is audited regularly.

## <span style="color:hsl(342,68%,44%)">Authorization Tokens</span>

Authorization information is embedded in access tokens issued during authentication.
Tokens typically include:

- User role
- Department
- Access scopes

## <span style="color:hsl(27,68%,44%)">Common Authorization Issues</span>

- Access denied due to missing role
- Stale permissions after role change
- Insufficient privileges for sensitive operations

## <span style="color:hsl(72,68%,32%)">Related Documents</span>

Refer to the IT Access Control Policy for detailed access request and review processes.
