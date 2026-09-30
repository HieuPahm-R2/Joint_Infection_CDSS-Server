# PJI Clinical Authorization

This context names the concepts used to decide who may use a backend capability
and which clinical records that person may access.

## Language

**Active Principal**:
An authenticated user whose account and assigned role are both active and whose
server-side session is still valid.
_Avoid_: Logged-in user, valid JWT

**Functional Permission**:
Permission to invoke one API operation, identified by its HTTP method and stable
route pattern. It does not by itself grant access to every object handled by the
operation.
_Avoid_: Full access, endpoint ownership

**Clinical Record**:
A patient or episode and the medical, diagnostic, imaging, recommendation and
decision data attached to it.
_Avoid_: Generic resource, row

**Care Team Membership**:
An explicit relationship granting an active principal access to one episode and
its attached clinical records.
_Avoid_: Implicit department access, guessed ownership

**Record Owner**:
The active principal who created a patient or episode and receives initial
access to it. Ownership does not replace professional-role rules for clinical
decisions.
_Avoid_: Administrator, unrestricted user

**Break-glass Administrator**:
An active `ADMIN` principal allowed to bypass clinical-record relationships for
recovery and administration; each bypass is a security-audit event.
_Avoid_: Normal clinical user, silent superuser
