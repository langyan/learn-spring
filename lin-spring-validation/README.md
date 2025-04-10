
### 创建


spring init --build=maven --java-version=17 --dependencies=web,validation --packaging=jar 
--a=lin-spring-validation 
--g=com.lin.spring.validation 
--package-name=com.lin.spring.validation
-n=lin-spring-validation lin-spring-validation;

### 流程

```
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
```