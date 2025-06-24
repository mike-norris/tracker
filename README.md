# Usage Examples

This document provides practical examples of using the Spring Boot Tracer Library in real-world scenarios.

## Table of Contents

- [Basic Web Application](#basic-web-application)
- [E-commerce Platform](#e-commerce-platform)
- [Microservices Architecture](#microservices-architecture)
- [Batch Processing System](#batch-processing-system)
- [API Gateway Integration](#api-gateway-integration)
- [Background Job Processing](#background-job-processing)
- [Performance Monitoring](#performance-monitoring)
- [Error Handling and Recovery](#error-handling-and-recovery)

---

## Basic Web Application

### User Registration and Login System

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final EmailService emailService;

    @PostMapping("/register")
    @TraceUserAction(value = "user_registration", category = "AUTHENTICATION")
    public ResponseEntity<UserResponse> registerUser(@RequestBody @Valid RegisterRequest request) {
        User user = userService.createUser(request);
        
        // Trigger welcome email asynchronously
        emailService.sendWelcomeEmailAsync(user.getId());
        
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PostMapping("/login")
    @TraceUserAction(value = "user_login", category = "AUTHENTICATION", measureTiming = true)
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = userService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}/profile")
    @TraceUserAction(value = "profile_view", category = "USER_INTERACTION")
    public ResponseEntity<UserProfile> getProfile(@PathVariable String userId) {
        UserProfile profile = userService.getUserProfile(userId);
        return ResponseEntity.ok(profile);
    }
}

@Service
public class UserService {

    @MeasurePerformance(value = "user_creation", slowThresholdMs = 2000)
    @PropagateTrace
    public User createUser(RegisterRequest request) {
        // Validate user data
        validateUserData(request);
        
        // Create user account
        User user = User.builder()
            .email(request.getEmail())
            .name(request.getName())
            .build();
            
        return userRepository.save(user);
    }

    @MeasurePerformance(value = "user_authentication")
    public LoginResponse authenticate(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new UserNotFoundException("User not found"));
            
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthenticationException("Invalid credentials");
        }
        
        String token = jwtService.generateToken(user);
        return new LoginResponse(token, user.getId());
    }
}

@Service
public class EmailService {

    @TraceJob(jobType = "EMAIL", jobName = "welcome_email", queueName = "notifications")
    @Async
    public void sendWelcomeEmailAsync(String userId) {
        User user = userService.findById(userId);
        sendWelcomeEmail(user);
    }

    @MeasurePerformance(value = "email_send", measureMemory = true)
    private void sendWelcomeEmail(User user) {
        // Email sending logic
        EmailTemplate template = templateService.getWelcomeTemplate();
        emailProvider.send(user.getEmail(), template.render(user));
    }
}
```

### Configuration

```yaml
tracing:
  enabled: true
  database:
    type: postgresql
    connection:
      url: jdbc:postgresql://localhost:5432/user_app
      username: app_user
      password: ${DB_PASSWORD}
  async:
    core-pool-size: 4
    max-pool-size: 8
    queue-capacity: 1000
```

---

## E-commerce Platform

### Product Catalog and Order Processing

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @GetMapping("/search")
    @TraceUserAction(value = "product_search", category = "CATALOG")
    @MeasurePerformance(value = "product_search", sample = true, sampleRate = 0.1)
    public ResponseEntity<List<Product>> searchProducts(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        SearchResult<Product> results = productService.searchProducts(query, page, size);
        return ResponseEntity.ok(results.getContent());
    }

    @GetMapping("/{productId}")
    @TraceUserAction(value = "product_view", category = "CATALOG")
    public ResponseEntity<ProductDetails> getProduct(@PathVariable String productId) {
        ProductDetails product = productService.getProductDetails(productId);
        return ResponseEntity.ok(product);
    }
}

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @PostMapping
    @TraceUserAction(value = "order_creation", category = "ORDER", measureTiming = true)
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        
        // Trigger order processing workflow
        orderProcessingService.processOrderAsync(order.getId());
        
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping("/{orderId}/status")
    @TraceUserAction(value = "order_status_check", category = "ORDER")
    public ResponseEntity<OrderStatus> getOrderStatus(@PathVariable String orderId) {
        OrderStatus status = orderService.getOrderStatus(orderId);
        return ResponseEntity.ok(status);
    }
}

@Service
public class OrderProcessingService {

    @TraceJob(jobType = "ORDER_PROCESSING", jobName = "process_order", priority = 7)
    @PropagateTrace
    public void processOrderAsync(String orderId) {
        Order order = orderService.findById(orderId);
        
        // Validate inventory
        inventoryService.validateInventory(order);
        
        // Process payment
        paymentService.processPayment(order);
        
        // Update order status
        orderService.updateStatus(orderId, OrderStatus.CONFIRMED);
        
        // Schedule fulfillment
        fulfillmentService.scheduleFulfillment(orderId);
    }

    @TraceJob(jobType = "INVENTORY", jobName = "inventory_check")
    @MeasurePerformance(value = "inventory_validation", slowThresholdMs = 3000)
    public void validateInventory(Order order) {
        for (OrderItem item : order.getItems()) {
            int available = inventoryRepository.getAvailableQuantity(item.getProductId());
            if (available < item.getQuantity()) {
                throw new InsufficientInventoryException(
                    "Not enough inventory for product: " + item.getProductId());
            }
        }
    }

    @TraceJob(jobType = "PAYMENT", jobName = "payment_processing", measureResources = true)
    @MeasurePerformance(value = "payment_processing", slowThresholdMs = 5000)
    public void processPayment(Order order) {
        PaymentRequest paymentRequest = PaymentRequest.builder()
            .amount(order.getTotalAmount())
            .currency(order.getCurrency())
            .paymentMethod(order.getPaymentMethod())
            .build();
            
        PaymentResult result = paymentGateway.processPayment(paymentRequest);
        
        if (!result.isSuccessful()) {
            throw new PaymentProcessingException("Payment failed: " + result.getErrorMessage());
        }
        
        orderService.updatePaymentStatus(order.getId(), PaymentStatus.COMPLETED);
    }
}
```

### Analytics and Reporting

```java
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @GetMapping("/products/popular")
    @TraceUserAction(value = "popular_products_report", category = "ANALYTICS")
    @MeasurePerformance(value = "analytics_query", slowThresholdMs = 10000)
    public ResponseEntity<List<PopularProduct>> getPopularProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        List<PopularProduct> products = analyticsService.getPopularProducts(startDate, endDate);
        return ResponseEntity.ok(products);
    }
}

@Service
public class AnalyticsService {

    @TraceJob(jobType = "ANALYTICS", jobName = "popular_products_analysis", priority = 3)
    public List<PopularProduct> getPopularProducts(LocalDate startDate, LocalDate endDate) {
        // Complex analytics query using tracing data
        Instant start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        
        List<UserAction> productViews = userActionRepository
            .findByActionAndTimeRange("product_view", start, end);
            
        return productViews.stream()
            .collect(groupingBy(this::extract