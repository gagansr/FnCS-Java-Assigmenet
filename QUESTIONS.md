# Questions

Here we have 3 questions related to the code base for you to answer. It is not about right or wrong, but more about what's the reasoning behind your decisions.

1. In this code base, we have some different implementation strategies when it comes to database access layer and manipulation. If you would maintain this code base, would you refactor any of those? Why?

**Answer:**
```txt
Yes, I would gradually refactor parts of the database access layer to improve consistency and maintainability.

The current codebase uses different approaches. The Store entity uses the active record pattern through Panache, with persistence operations called directly from StoreResource. The Product implementation uses a separate ProductRepository, while the Warehouse implementation follows a more layered approach with domain models, ports, use cases, and a repository that maps database entities to domain models.

For a small application, these differences are manageable. However, as the application grows, having different patterns for similar operations can make the code harder to understand, test, and maintain.

I would prefer the repository pattern for consistency, especially for business-critical operations. Business rules would remain in use cases or services, while repositories would be responsible for database access and entity mapping. This would make unit testing easier and reduce coupling between REST resources and persistence logic.

I would approach this incrementally rather than rewriting everything at once. I would prioritize areas with complex business rules, frequent changes, or inadequate test coverage, while avoiding unnecessary abstractions for simple CRUD operations.
```
----
2. When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded directly everything. What would be your thoughts about what are the pros and cons of each approach and what would be your choice?

**Answer:**
```txt
Both approaches have advantages, and I would choose based on the API's stability, consumers, and the team's development workflow.

With an OpenAPI-first approach, the API contract is defined in a specification and code can be generated from it. This improves consistency between documentation and implementation, makes request and response structures explicit, and enables client generation and contract validation. It is particularly valuable when multiple teams or external consumers depend on the API.

The disadvantages are additional tooling and build configuration, a need to maintain the specification, and potential complexity when generated code must be customized. Generated code should not contain business logic, which belongs in application services or use cases.

With manually implemented endpoints, developers have more direct control and can move quickly for small or internal APIs. The approach is easier to start with, but API definitions, validation rules, and documentation can become inconsistent as the application evolves.

For this project, I would favor OpenAPI-first development for stable APIs that have multiple consumers or require a clearly defined contract. I would keep business logic in separately implemented components and use the generated interfaces and DTOs as the API boundary. I would also consider adopting a consistent approach across Product, Store, and Warehouse APIs where the benefits justify the migration effort.
```
----
3. Given the need to balance thorough testing with time and resource constraints, how would you prioritize and implement tests for this project? Which types of tests would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**
```txt
I would prioritize tests based on business risk, the impact of failure, and the complexity of the behavior rather than aiming only for a high code-coverage percentage.

First, I would write unit tests for business rules in the Warehouse use cases. These would cover valid creation, duplicate business unit codes, invalid locations, warehouse-count limits, capacity constraints, stock validation, replacement stock matching, and insufficient replacement capacity. Negative cases are especially important because they protect business invariants.

Second, I would add repository integration tests using a real PostgreSQL instance, for example through Testcontainers. These tests would verify persistence, entity-to-domain mapping, generated IDs, and archive behavior.

Third, I would focus on REST API integration tests using Quarkus and RestAssured. These would validate the main endpoints, HTTP status codes, request validation, missing resources, and the visibility of archived warehouses.

For Store operations, I would specifically test transaction behavior: the legacy gateway should be invoked only after a successful database commit and should not be invoked when the transaction rolls back.

I would run the existing tests and new relevant tests in CI on every change. Over time, I would review coverage reports, keep tests deterministic, and update regression tests whenever a defect is fixed. Coverage metrics would be used as a guide, not as the only measure of test quality.

Given limited time, I would prioritize critical business validations and transaction consistency first, followed by repository and API integration coverage. This gives a useful balance between confidence, execution time, and maintenance cost.
```