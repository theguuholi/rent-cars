# Form validation ownership

Status: Accepted for the React application.

When a form has related input constraints or needs normalization before submission, its feature owns a colocated Zod schema. The schema converts draft values into the API input and supplies field errors. The component owns draft state, user interaction, requests, and server errors. The Spring API validates the HTTP contract and business rules independently.

We considered keeping checks and normalization in each component. That duplicated the rules used for field feedback and submission, and allowed registration to validate normalized values while sending the unnormalized draft. We also considered a shared schema for all forms, but the fields and business meanings differ between registration, cars, and specifications.

Simple fields and time-dependent checks can remain local when a schema would only add indirection. A schema does not replace API validation or database constraints.
