


``
Client Request
     ↓
DispatcherServlet
     ↓
HandlerMethodArgumentResolver (RequestResponseBodyMethodProcessor)
     ↓
JSON → Java Object Mapping
     ↓
Validation using Hibernate Validator
     ↓
 ┌───────────────┐
 │  Validation   │
 │   Passed?     │
 └──────┬────────┘
        ↓ No
Throws MethodArgumentNotValidException
        ↓
Caught by GlobalExceptionHandler
        ↓
Returns 400 Bad Request with error map
``