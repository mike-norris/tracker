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
            .collect(groupingBy(this::extractProductId, counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .map(entry -> new PopularProduct(entry.getKey(), entry.getValue()))
            .collect(toList());
    }

    private String extractProductId(UserAction action) {
        // Extract product ID from endpoint or action data
        if (action.endpoint() != null) {
            return action.endpoint().replaceAll(".*/products/([^/]+).*", "$1");
        }
        return "unknown";
    }
}
```

---

## Microservices Architecture

### Service-to-Service Tracing

```java
// User Service
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final OrderServiceClient orderServiceClient;

    @GetMapping("/{userId}/orders")
    @TraceUserAction(value = "user_orders_view", category = "USER_INTERACTION")
    @PropagateTrace(autoGenerate = true)
    public ResponseEntity<List<Order>> getUserOrders(@PathVariable String userId) {
        // Trace context is automatically propagated to the order service
        List<Order> orders = orderServiceClient.getOrdersByUserId(userId);
        return ResponseEntity.ok(orders);
    }
}

// Feign Client for inter-service communication
@FeignClient(name = "order-service", url = "${services.order.url}")
public interface OrderServiceClient {

    @GetMapping("/api/orders/user/{userId}")
    @TraceUserAction(value = "order_service_call", category = "SERVICE_CALL")
    List<Order> getOrdersByUserId(@PathVariable String userId);
}

// Order Service
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final PaymentServiceClient paymentServiceClient;

    @GetMapping("/user/{userId}")
    @TraceUserAction(value = "orders_by_user", category = "ORDER")
    @MeasurePerformance(value = "order_lookup")
    public ResponseEntity<List<Order>> getOrdersByUserId(@PathVariable String userId) {
        List<Order> orders = orderService.findByUserId(userId);
        
        // Enrich with payment information
        for (Order order : orders) {
            PaymentInfo payment = paymentServiceClient.getPaymentInfo(order.getId());
            order.setPaymentInfo(payment);
        }
        
        return ResponseEntity.ok(orders);
    }
}

// Payment Service
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @GetMapping("/order/{orderId}")
    @TraceUserAction(value = "payment_info_lookup", category = "PAYMENT")
    @MeasurePerformance(value = "payment_lookup", measureMemory = true)
    public ResponseEntity<PaymentInfo> getPaymentInfo(@PathVariable String orderId) {
        PaymentInfo payment = paymentService.findByOrderId(orderId);
        return ResponseEntity.ok(payment);
    }
}
```

### Distributed Tracing Configuration

```yaml
# User Service (application.yml)
spring:
  application:
    name: user-service

tracing:
  enabled: true
  trace-id-header: X-Trace-ID
  database:
    type: postgresql
    connection:
      url: jdbc:postgresql://localhost:5432/user_service
      username: user_service
      password: ${DB_PASSWORD}

services:
  order:
    url: http://order-service:8081

# Order Service (application.yml)
spring:
  application:
    name: order-service

tracing:
  enabled: true
  database:
    type: postgresql
    connection:
      url: jdbc:postgresql://localhost:5432/order_service
      username: order_service
      password: ${DB_PASSWORD}

services:
  payment:
    url: http://payment-service:8082

# Payment Service (application.yml)
spring:
  application:
    name: payment-service

tracing:
  enabled: true
  database:
    type: mongodb
    mongodb:
      uri: mongodb://localhost:27017/payment_service
      database: payment_service
```

---

## Batch Processing System

### Data Processing Pipeline

```java
@Component
public class DataProcessingJob {

    private final DataProcessor dataProcessor;
    private final NotificationService notificationService;

    @TraceJob(jobType = "BATCH", jobName = "daily_data_processing", priority = 5)
    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
    @MeasurePerformance(value = "daily_batch_job", measureResources = true)
    public void processDailyData() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        
        try {
            // Process user engagement data
            processUserEngagementData(yesterday);
            
            // Generate reports
            generateDailyReports(yesterday);
            
            // Send notifications
            notificationService.sendProcessingCompleteNotification(yesterday);
            
        } catch (Exception e) {
            logger.error("Daily data processing failed for date: {}", yesterday, e);
            notificationService.sendProcessingFailedNotification(yesterday, e.getMessage());
            throw e;
        }
    }

    @TraceJob(jobType = "DATA_PROCESSING", jobName = "user_engagement_processing")
    @PropagateTrace
    private void processUserEngagementData(LocalDate date) {
        Instant startTime = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTime = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        
        // Fetch user actions for the day
        List<UserAction> userActions = userActionRepository
            .findByActionAndTimeRange("product_view", startTime, endTime);
        
        // Process in chunks to avoid memory issues
        int chunkSize = 1000;
        for (int i = 0; i < userActions.size(); i += chunkSize) {
            int endIndex = Math.min(i + chunkSize, userActions.size());
            List<UserAction> chunk = userActions.subList(i, endIndex);
            
            processUserActionChunk(chunk, date);
        }
    }

    @TraceJob(jobType = "DATA_PROCESSING", jobName = "user_action_chunk_processing")
    @MeasurePerformance(value = "chunk_processing", slowThresholdMs = 30000)
    private void processUserActionChunk(List<UserAction> chunk, LocalDate date) {
        Map<String, UserEngagementStats> engagementStats = chunk.stream()
            .collect(groupingBy(UserAction::userId, 
                collectingAndThen(toList(), this::calculateEngagementStats)));
        
        // Save aggregated stats
        engagementStatsRepository.saveAll(
            engagementStats.entrySet().stream()
                .map(entry -> UserEngagementSummary.builder()
                    .userId(entry.getKey())
                    .date(date)
                    .stats(entry.getValue())
                    .build())
                .collect(toList())
        );
    }

    @TraceJob(jobType = "REPORTING", jobName = "daily_report_generation")
    private void generateDailyReports(LocalDate date) {
        // Generate executive dashboard data
        generateExecutiveDashboard(date);
        
        // Generate department reports
        generateDepartmentReports(date);
        
        // Generate performance alerts
        generatePerformanceAlerts(date);
    }

    @TraceJob(jobType = "REPORTING", jobName = "executive_dashboard")
    @MeasurePerformance(value = "executive_dashboard_generation")
    private void generateExecutiveDashboard(LocalDate date) {
        DashboardData dashboard = DashboardData.builder()
            .date(date)
            .totalUsers(calculateTotalActiveUsers(date))
            .totalOrders(calculateTotalOrders(date))
            .revenue(calculateDailyRevenue(date))
            .conversionRate(calculateConversionRate(date))
            .build();
        
        dashboardRepository.save(dashboard);
    }
}
```

### Batch Job Monitoring

```java
@RestController
@RequestMapping("/api/admin/jobs")
public class JobMonitoringController {

    private final JobExecutionRepository jobExecutionRepository;

    @GetMapping("/status")
    @TraceUserAction(value = "job_status_check", category = "ADMIN")
    public ResponseEntity<List<JobStatus>> getJobStatuses() {
        List<JobExecution> runningJobs = jobExecutionRepository
            .findByStatus(JobStatus.RUNNING);
        
        List<JobExecution> pendingJobs = jobExecutionRepository
            .findByStatus(JobStatus.PENDING);
        
        List<JobExecution> failedJobs = jobExecutionRepository
            .findByStatus(JobStatus.FAILED, 10);
        
        JobStatusSummary summary = JobStatusSummary.builder()
            .runningJobs(runningJobs.size())
            .pendingJobs(pendingJobs.size())
            .failedJobs(failedJobs.size())
            .recentFailures(failedJobs)
            .build();
        
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/performance")
    @TraceUserAction(value = "job_performance_report", category = "ADMIN")
    @MeasurePerformance(value = "job_performance_analysis")
    public ResponseEntity<JobPerformanceReport> getJobPerformance(
            @RequestParam(defaultValue = "7") int days) {
        
        Instant startTime = Instant.now().minus(days, ChronoUnit.DAYS);
        Instant endTime = Instant.now();
        
        List<JobTypeStatistics> jobStats = jobExecutionRepository
            .getJobTypeStatistics(startTime, endTime);
        
        List<QueueStatistics> queueStats = jobExecutionRepository
            .getQueueStatistics();
        
        JobPerformanceReport report = JobPerformanceReport.builder()
            .period(String.format("Last %d days", days))
            .jobTypeStats(jobStats)
            .queueStats(queueStats)
            .generatedAt(Instant.now())
            .build();
        
        return ResponseEntity.ok(report);
    }
}
```

---

## API Gateway Integration

### Gateway-Level Tracing

```java
@Component
public class TracingGatewayFilter implements GlobalFilter, Ordered {

    private final TracingService tracingService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Generate or extract trace ID
        String traceId = extractOrGenerateTraceId(request);
        
        // Set trace context
        TraceContext.setTraceId(UUID.fromString(traceId));
        TraceContext.setUserId(extractUserId(request));
        
        // Add trace ID to response headers
        exchange.getResponse().getHeaders().add("X-Trace-ID", traceId);
        
        // Trace the gateway request
        logGatewayRequest(request);
        
        long startTime = System.currentTimeMillis();
        
        return chain.filter(exchange)
            .doFinally(signalType -> {
                long duration = System.currentTimeMillis() - startTime;
                logGatewayResponse(request, exchange.getResponse(), duration);
                TraceContext.clear();
            });
    }

    @TraceUserAction(value = "gateway_request", category = "GATEWAY", async = true)
    private void logGatewayRequest(ServerHttpRequest request) {
        // Gateway request logging
    }

    @TraceUserAction(value = "gateway_response", category = "GATEWAY", async = true)
    private void logGatewayResponse(ServerHttpRequest request, 
                                   ServerHttpResponse response, 
                                   long duration) {
        // Gateway response logging with timing
    }

    @Override
    public int getOrder() {
        return -1; // High priority
    }
}
```

### Route-Specific Monitoring

```java
@Configuration
public class GatewayRoutingConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("user-service", r -> r
                .path("/api/users/**")
                .filters(f -> f
                    .filter(new ServiceCallTracingFilter("user-service"))
                    .circuitBreaker(config -> config
                        .setName("user-service-cb")
                        .setFallbackUri("forward:/fallback/user-service")))
                .uri("http://user-service:8080"))
            
            .route("order-service", r -> r
                .path("/api/orders/**")
                .filters(f -> f
                    .filter(new ServiceCallTracingFilter("order-service"))
                    .circuitBreaker(config -> config
                        .setName("order-service-cb")
                        .setFallbackUri("forward:/fallback/order-service")))
                .uri("http://order-service:8080"))
            
            .build();
    }
}

public class ServiceCallTracingFilter implements GatewayFilter {
    
    private final String serviceName;
    
    public ServiceCallTracingFilter(String serviceName) {
        this.serviceName = serviceName;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
            .doOnSuccess(v -> logServiceCall(exchange, true))
            .doOnError(e -> logServiceCall(exchange, false));
    }
    
    @TraceUserAction(value = "service_call", category = "GATEWAY")
    private void logServiceCall(ServerWebExchange exchange, boolean success) {
        // Log service call with success/failure status
    }
}
```

---

## Background Job Processing

### Async Task Processing

```java
@Service
public class AsyncTaskProcessor {

    @TraceJob(jobType = "IMAGE_PROCESSING", jobName = "image_resize", queueName = "media")
    @Async("taskExecutor")
    @MeasurePerformance(value = "image_processing", measureResources = true)
    public CompletableFuture<ProcessingResult> processImageAsync(String imageId, ImageProcessingRequest request) {
        try {
            // Download original image
            BufferedImage originalImage = downloadImage(imageId);
            
            // Process image (resize, crop, etc.)
            BufferedImage processedImage = processImage(originalImage, request);
            
            // Upload processed image
            String processedImageUrl = uploadProcessedImage(processedImage, imageId);
            
            // Update database
            updateImageMetadata(imageId, processedImageUrl, request);
            
            return CompletableFuture.completedFuture(
                ProcessingResult.success(processedImageUrl)
            );
            
        } catch (Exception e) {
            logger.error("Image processing failed for imageId: {}", imageId, e);
            return CompletableFuture.completedFuture(
                ProcessingResult.failure(e.getMessage())
            );
        }
    }

    @TraceJob(jobType = "EMAIL_CAMPAIGN", jobName = "bulk_email_send", queueName = "notifications", priority = 3)
    @Async("emailExecutor")
    public CompletableFuture<Void> sendBulkEmailAsync(String campaignId, List<String> recipients) {
        try {
            EmailCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CampaignNotFoundException("Campaign not found: " + campaignId));
            
            // Process recipients in batches
            int batchSize = 100;
            for (int i = 0; i < recipients.size(); i += batchSize) {
                List<String> batch = recipients.subList(i, Math.min(i + batchSize, recipients.size()));
                processBatch(campaign, batch);
                
                // Add delay to avoid overwhelming email service
                Thread.sleep(1000);
            }
            
            // Update campaign status
            campaignRepository.updateStatus(campaignId, CampaignStatus.COMPLETED);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            logger.error("Bulk email sending failed for campaign: {}", campaignId, e);
            campaignRepository.updateStatus(campaignId, CampaignStatus.FAILED);
            throw new RuntimeException(e);
        }
    }

    @TraceJob(jobType = "EMAIL_BATCH", jobName = "email_batch_processing")
    @MeasurePerformance(value = "email_batch_send")
    private void processBatch(EmailCampaign campaign, List<String> recipients) {
        for (String recipient : recipients) {
            try {
                emailService.sendCampaignEmail(campaign, recipient);
                
                // Track email sent
                emailTrackingService.trackEmailSent(campaign.getId(), recipient);
                
            } catch (Exception e) {
                logger.warn("Failed to send email to {} for campaign {}", recipient, campaign.getId(), e);
                emailTrackingService.trackEmailFailed(campaign.getId(), recipient, e.getMessage());
            }
        }
    }
}
```

### Scheduled Job Management

```java
@Component
public class ScheduledJobManager {

    @TraceJob(jobType = "MAINTENANCE", jobName = "database_cleanup", priority = 2)
    @Scheduled(cron = "0 0 3 * * ?") // Run at 3 AM daily
    @MeasurePerformance(value = "database_cleanup", measureResources = true)
    public void performDatabaseCleanup() {
        // Clean up old trace records
        int deletedRecords = tracingRepository.deleteRecordsOlderThan(
            Instant.now().minus(properties.getRetentionMonths() * 30, ChronoUnit.DAYS)
        );
        
        logger.info("Database cleanup completed. Deleted {} records", deletedRecords);
    }

    @TraceJob(jobType = "MAINTENANCE", jobName = "cache_refresh", priority = 4)
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @MeasurePerformance(value = "cache_refresh")
    public void refreshCache() {
        // Refresh application caches
        cacheManager.getCache("products").clear();
        cacheManager.getCache("categories").clear();
        
        // Pre-populate with fresh data
        productService.preloadPopularProducts();
        categoryService.preloadCategories();
    }

    @TraceJob(jobType = "HEALTH_CHECK", jobName = "system_health_check", priority = 8)
    @Scheduled(fixedRate = 60000) // Every minute
    @MeasurePerformance(value = "health_check")
    public void performHealthCheck() {
        // Check database connectivity
        boolean dbHealthy = checkDatabaseHealth();
        
        // Check external service connectivity
        boolean servicesHealthy = checkExternalServices();
        
        // Check disk space
        boolean diskSpaceOk = checkDiskSpace();
        
        if (!dbHealthy || !servicesHealthy || !diskSpaceOk) {
            alertService.sendHealthAlert(
                HealthStatus.builder()
                    .database(dbHealthy)
                    .externalServices(servicesHealthy)
                    .diskSpace(diskSpaceOk)
                    .timestamp(Instant.now())
                    .build()
            );
        }
    }
}
```

---

## Performance Monitoring

### Real-time Performance Tracking

```java
@RestController
@RequestMapping("/api/monitoring")
public class PerformanceMonitoringController {

    @GetMapping("/performance/real-time")
    @TraceUserAction(value = "realtime_performance_view", category = "MONITORING")
    @MeasurePerformance(value = "performance_dashboard_load")
    public ResponseEntity<RealTimePerformanceData> getRealTimePerformance() {
        Instant now = Instant.now();
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        
        // Get recent user actions
        List<UserAction> recentActions = userActionRepository
            .findByUserIdAndTimeRange("", oneHourAgo, now);
        
        // Calculate performance metrics
        double avgResponseTime = recentActions.stream()
            .filter(action -> action.durationMs() != null)
            .mapToLong(UserAction::durationMs)
            .average()
            .orElse(0.0);
        
        long errorCount = recentActions.stream()
            .filter(action -> action.responseStatus() != null && action.responseStatus() >= 400)
            .count();
        
        double errorRate = recentActions.isEmpty() ? 0.0 : 
            (errorCount * 100.0) / recentActions.size();
        
        // Get active job count
        long activeJobs = jobExecutionRepository.countByStatus(JobStatus.RUNNING);
        long pendingJobs = jobExecutionRepository.countByStatus(JobStatus.PENDING);
        
        RealTimePerformanceData data = RealTimePerformanceData.builder()
            .timestamp(now)
            .avgResponseTimeMs(avgResponseTime)
            .errorRate(errorRate)
            .requestsPerMinute(recentActions.size())
            .activeJobs(activeJobs)
            .pendingJobs(pendingJobs)
            .build();
        
        return ResponseEntity.ok(data);
    }

    @GetMapping("/performance/trends")
    @TraceUserAction(value = "performance_trends_view", category = "MONITORING")
    public ResponseEntity<PerformanceTrends> getPerformanceTrends(
            @RequestParam(defaultValue = "24") int hours) {
        
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(hours, ChronoUnit.HOURS);
        
        // Get hourly performance data
        List<HourlyPerformanceData> hourlyData = calculateHourlyPerformanceData(startTime, endTime);
        
        PerformanceTrends trends = PerformanceTrends.builder()
            .period(String.format("Last %d hours", hours))
            .hourlyData(hourlyData)
            .averageResponseTime(hourlyData.stream()
                .mapToDouble(HourlyPerformanceData::getAvgResponseTime)
                .average().orElse(0.0))
            .peakResponseTime(hourlyData.stream()
                .mapToDouble(HourlyPerformanceData::getPeakResponseTime)
                .max().orElse(0.0))
            .averageErrorRate(hourlyData.stream()
                .mapToDouble(HourlyPerformanceData::getErrorRate)
                .average().orElse(0.0))
            .build();
        
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/alerts")
    @TraceUserAction(value = "performance_alerts_view", category = "MONITORING")
    public ResponseEntity<List<PerformanceAlert>> getPerformanceAlerts() {
        List<PerformanceAlert> alerts = new ArrayList<>();
        
        // Check for slow operations
        List<UserAction> slowActions = userActionRepository.findSlowActions(
            5000, // 5 second threshold
            Instant.now().minus(1, ChronoUnit.HOURS),
            Instant.now()
        );
        
        if (!slowActions.isEmpty()) {
            alerts.add(PerformanceAlert.builder()
                .type(AlertType.SLOW_RESPONSE)
                .severity(AlertSeverity.WARNING)
                .message(String.format("Found %d slow operations in the last hour", slowActions.size()))
                .count(slowActions.size())
                .timestamp(Instant.now())
                .build());
        }
        
        // Check for high error rates
        long recentActions = userActionRepository.countByUserIdAndTimeRange("",
            Instant.now().minus(1, ChronoUnit.HOURS), Instant.now());
        long errorActions = userActionRepository.findByResponseStatusRange(400, 599,
            Instant.now().minus(1, ChronoUnit.HOURS), Instant.now()).size();
        
        double errorRate = recentActions > 0 ? (errorActions * 100.0) / recentActions : 0.0;
        
        if (errorRate > 5.0) { // 5% error rate threshold
            alerts.add(PerformanceAlert.builder()
                .type(AlertType.HIGH_ERROR_RATE)
                .severity(errorRate > 10.0 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING)
                .message(String.format("Error rate is %.1f%% in the last hour", errorRate))
                .value(errorRate)
                .timestamp(Instant.now())
                .build());
        }
        
        return ResponseEntity.ok(alerts);
    }
}
```

### Custom Performance Metrics

```java
@Component
public class CustomPerformanceCollector {

    private final MeterRegistry meterRegistry;
    private final Counter requestCounter;
    private final Timer responseTimer;
    private final Gauge activeUsersGauge;

    public CustomPerformanceCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.requestCounter = Counter.builder("api.requests.total")
            .description("Total API requests")
            .register(meterRegistry);
        this.responseTimer = Timer.builder("api.response.time")
            .description("API response time")
            .register(meterRegistry);
        this.activeUsersGauge = Gauge.builder("api.users.active")
            .description("Currently active users")
            .register(meterRegistry, this, CustomPerformanceCollector::getActiveUserCount);
    }

    @EventListener
    @TraceUserAction(value = "performance_metric_collection", category = "MONITORING", async = true)
    public void handleUserAction(UserActionEvent event) {
        UserAction action = event.getUserAction();
        
        // Increment request counter
        requestCounter.increment(
            Tags.of(
                "action", action.action(),
                "endpoint", action.endpoint() != null ? action.endpoint() : "unknown",
                "status", action.responseStatus() != null ? 
                    action.responseStatus().toString() : "unknown"
            )
        );
        
        // Record response time
        if (action.durationMs() != null) {
            responseTimer.record(action.durationMs(), TimeUnit.MILLISECONDS,
                Tags.of("action", action.action())
            );
        }
    }

    @EventListener
    @TraceUserAction(value = "job_metric_collection", category = "MONITORING", async = true)
    public void handleJobExecution(JobExecutionEvent event) {
        JobExecution job = event.getJobExecution();
        
        // Track job metrics
        Counter.builder("jobs.executions.total")
            .description("Total job executions")
            .tags("type", job.jobType(), "status", job.status().name())
            .register(meterRegistry)
            .increment();
        
        if (job.durationMs() != null) {
            Timer.builder("jobs.execution.time")
                .description("Job execution time")
                .tags("type", job.jobType())
                .register(meterRegistry)
                .record(job.durationMs(), TimeUnit.MILLISECONDS);
        }
    }

    private double getActiveUserCount() {
        // Calculate active users in the last 15 minutes
        Instant fifteenMinutesAgo = Instant.now().minus(15, ChronoUnit.MINUTES);
        return userActionRepository.countUniqueUsersInTimeRange(fifteenMinutesAgo, Instant.now());
    }
}
```

---

## Error Handling and Recovery

### Resilient Service Design

```java
@Service
public class ResilientOrderService {

    private final CircuitBreaker paymentServiceCircuitBreaker;
    private final CircuitBreaker inventoryServiceCircuitBreaker;
    private final RetryTemplate retryTemplate;

    @TraceUserAction(value = "resilient_order_creation", category = "ORDER")
    @MeasurePerformance(value = "resilient_order_processing", slowThresholdMs = 10000)
    public OrderResult createOrderWithResilience(CreateOrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        
        try {
            // Step 1: Validate order with retry
            validateOrderWithRetry(request);
            
            // Step 2: Check inventory with circuit breaker
            boolean inventoryAvailable = checkInventoryWithCircuitBreaker(request);
            if (!inventoryAvailable) {
                return OrderResult.failure("Insufficient inventory");
            }
            
            // Step 3: Create order record
            Order order = createOrderRecord(request, orderId);
            
            // Step 4: Process payment with circuit breaker and fallback
            PaymentResult paymentResult = processPaymentWithCircuitBreaker(order);
            if (!paymentResult.isSuccessful()) {
                // Mark order as pending payment
                order.setStatus(OrderStatus.PENDING_PAYMENT);
                orderRepository.save(order);
                schedulePaymentRetry(orderId);
                return OrderResult.partialSuccess(orderId, "Payment processing delayed");
            }
            
            // Step 5: Finalize order
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
            
            return OrderResult.success(orderId);
            
        } catch (Exception e) {
            logger.error("Order creation failed for request: {}", request, e);
            handleOrderCreationFailure(orderId, e);
            return OrderResult.failure("Order creation failed: " + e.getMessage());
        }
    }

    @TraceJob(jobType = "ORDER_VALIDATION", jobName = "order_validation_with_retry")
    private void validateOrderWithRetry(CreateOrderRequest request) {
        retryTemplate.execute(context -> {
            // Validation logic that may fail temporarily
            validateOrderItems(request.getItems());
            validateCustomerEligibility(request.getCustomerId());
            validateDeliveryAddress(request.getDeliveryAddress());
            return null;
        });
    }

    @TraceJob(jobType = "INVENTORY_CHECK", jobName = "inventory_check_with_circuit_breaker")
    @MeasurePerformance(value = "inventory_check_resilient")
    private boolean checkInventoryWithCircuitBreaker(CreateOrderRequest request) {
        return inventoryServiceCircuitBreaker.executeSupplier(() -> {
            for (OrderItem item : request.getItems()) {
                if (!inventoryService.isAvailable(item.getProductId(), item.getQuantity())) {
                    return false;
                }
            }
            return true;
        });
    }

    @TraceJob(jobType = "PAYMENT_PROCESSING", jobName = "payment_with_circuit_breaker")
    @MeasurePerformance(value = "payment_processing_resilient")
    private PaymentResult processPaymentWithCircuitBreaker(Order order) {
        return paymentServiceCircuitBreaker.executeSupplier(() -> {
            return paymentService.processPayment(PaymentRequest.builder()
                .orderId(order.getId())
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .paymentMethod(order.getPaymentMethod())
                .build());
        });
    }

    @TraceJob(jobType = "ORDER_RECOVERY", jobName = "payment_retry_scheduling")
    private void schedulePaymentRetry(String orderId) {
        // Schedule payment retry job
        PaymentRetryJob retryJob = PaymentRetryJob.builder()
            .orderId(orderId)
            .scheduledTime(Instant.now().plus(5, ChronoUnit.MINUTES))
            .retryCount(0)
            .maxRetries(3)
            .build();
        
        jobSchedulingService.scheduleJob(retryJob);
    }

    @TraceJob(jobType = "ORDER_RECOVERY", jobName = "order_failure_handling")
    private void handleOrderCreationFailure(String orderId, Exception e) {
        // Log failure for analysis
        OrderFailureLog failureLog = OrderFailureLog.builder()
            .orderId(orderId)
            .errorMessage(e.getMessage())
            .stackTrace(getStackTrace(e))
            .timestamp(Instant.now())
            .build();
        
        orderFailureRepository.save(failureLog);
        
        // Notify monitoring system
        alertService.sendOrderCreationAlert(orderId, e.getMessage());
        
        // Schedule cleanup if partial order was created
        if (orderRepository.existsById(orderId)) {
            scheduleOrderCleanup(orderId);
        }
    }
}
```

### Monitoring and Alerting

```java
@Component
public class ErrorMonitoringService {

    @EventListener
    @TraceUserAction(value = "error_monitoring", category = "MONITORING", async = true)
    public void handleApplicationError(ApplicationErrorEvent event) {
        Throwable error = event.getThrowable();
        String context = event.getContext();
        
        // Classify error type
        ErrorType errorType = classifyError(error);
        ErrorSeverity severity = determineSeverity(error, context);
        
        // Log error for analysis
        ErrorLog errorLog = ErrorLog.builder()
            .errorType(errorType)
            .severity(severity)
            .message(error.getMessage())
            .stackTrace(getStackTrace(error))
            .context(context)
            .timestamp(Instant.now())
            .build();
        
        errorLogRepository.save(errorLog);
        
        // Check if this is a critical error requiring immediate attention
        if (severity == ErrorSeverity.CRITICAL || isHighFrequencyError(error)) {
            sendImmediateAlert(errorLog);
        }
        
        // Update error rate metrics
        updateErrorRateMetrics(errorType, context);
    }

    @TraceJob(jobType = "ERROR_ANALYSIS", jobName = "error_pattern_analysis")
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @MeasurePerformance(value = "error_pattern_analysis")
    public void analyzeErrorPatterns() {
        Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
        
        // Get recent errors
        List<ErrorLog> recentErrors = errorLogRepository
            .findByTimestampGreaterThan(fiveMinutesAgo);
        
        // Group by error type and message
        Map<String, List<ErrorLog>> errorGroups = recentErrors.stream()
            .collect(groupingBy(error -> error.getErrorType() + ":" + error.getMessage()));
        
        // Check for error spikes
        for (Map.Entry<String, List<ErrorLog>> entry : errorGroups.entrySet()) {
            if (entry.getValue().size() >= 10) { // 10+ occurrences in 5 minutes
                sendErrorSpikeAlert(entry.getKey(), entry.getValue().size());
            }
        }
        
        // Check for new error types
        Set<String> recentErrorTypes = recentErrors.stream()
            .map(ErrorLog::getErrorType)
            .collect(toSet());
        
        Set<String> historicalErrorTypes = getHistoricalErrorTypes();
        
        Set<String> newErrorTypes = Sets.difference(recentErrorTypes, historicalErrorTypes);
        if (!newErrorTypes.isEmpty()) {
            sendNewErrorTypeAlert(newErrorTypes);
        }
    }

    @TraceJob(jobType = "SYSTEM_RECOVERY", jobName = "automatic_recovery")
    @MeasurePerformance(value = "automatic_recovery")
    public void attemptAutomaticRecovery(String errorType, String context) {
        switch (errorType) {
            case "DATABASE_CONNECTION_ERROR":
                // Attempt to refresh connection pool
                databaseHealthService.refreshConnectionPool();
                break;
                
            case "CACHE_TIMEOUT_ERROR":
                // Clear and refresh cache
                cacheManager.getCache(context).clear();
                cacheWarmupService.warmupCache(context);
                break;
                
            case "EXTERNAL_SERVICE_TIMEOUT":
                // Reset circuit breaker if it's stuck open
                circuitBreakerRegistry.circuitBreaker(context).reset();
                break;
                
            case "MEMORY_PRESSURE":
                // Trigger garbage collection and reduce batch sizes
                System.gc();
                batchSizeOptimizer.reduceBatchSizes();
                break;
                
            default:
                logger.info("No automatic recovery available for error type: {}", errorType);
        }
    }

    private boolean isHighFrequencyError(Throwable error) {
        String errorSignature = error.getClass().getSimpleName() + ":" + error.getMessage();
        
        // Check if this error occurred more than 5 times in the last minute
        Instant oneMinuteAgo = Instant.now().minus(1, ChronoUnit.MINUTES);
        long recentOccurrences = errorLogRepository
            .countByErrorSignatureAndTimestampGreaterThan(errorSignature, oneMinuteAgo);
        
        return recentOccurrences >= 5;
    }
}
```

---

## Configuration Best Practices

### Environment-Specific Configuration

```yaml
# application.yml (base configuration)
tracing:
  enabled: true
  trace-id-header: X-Trace-ID
  auto-create-tables: true
  
  database:
    user-actions-table: user_actions
    job-executions-table: job_executions
    batch-size: 1000
    
  async:
    enabled: true
    thread-pool-name: tracing-executor
    
  monitoring:
    metrics-enabled: true
    health-check: true

---
# application-development.yml
spring:
  config:
    activate:
      on-profile: development

tracing:
  database:
    type: postgresql
    connection:
      url: jdbc:postgresql://localhost:5432/myapp_dev
      username: developer
      password: devpassword
      pool:
        maximum-pool-size: 5
        minimum-idle: 1
  
  async:
    core-pool-size: 2
    max-pool-size: 4
    queue-capacity: 100

logging:
  level:
    com.openrangelabs.tracer: DEBUG

---
# application-staging.yml
spring:
  config:
    activate:
      on-profile: staging

tracing:
  database:
    type: postgresql
    connection:
      url: jdbc:postgresql://staging-db:5432/myapp_staging
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
      pool:
        maximum-pool-size: 10
        minimum-idle: 3
  
  async:
    core-pool-size: 4
    max-pool-size: 8
    queue-capacity: 500

---
# application-production.yml
spring:
  config:
    activate:
      on-profile: production

tracing:
  retention-months: 24
  
  database:
    type: postgresql
    enable-partitioning: true
    batch-size: 2000
    connection:
      url: jdbc:postgresql://prod-db-cluster:5432/myapp_prod
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
      pool:
        maximum-pool-size: 20
        minimum-idle: 5
        connection-timeout-ms: 30000
        idle-timeout-ms: 600000
        max-lifetime-ms: 1800000
  
  async:
    core-pool-size: 8
    max-pool-size: 16
    queue-capacity: 2000
    keep-alive-seconds: 120

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,tracing
  endpoint:
    health:
      show-details: when-authorized
```

---

## Summary

These examples demonstrate:

1. **Basic Web Application**: Simple user registration and authentication with tracing
2. **E-commerce Platform**: Complex order processing with multiple service interactions
3. **Microservices Architecture**: Distributed tracing across multiple services
4. **Batch Processing**: Large-scale data processing with performance monitoring
5. **API Gateway Integration**: Gateway-level tracing and routing
6. **Background Job Processing**: Async task processing with comprehensive monitoring
7. **Performance Monitoring**: Real-time performance tracking and alerting
8. **Error Handling and Recovery**: Resilient design with automatic recovery mechanisms

Each example showcases different aspects of the Tracer library:
- Annotation usage patterns
- Performance optimization techniques
- Error handling strategies
- Monitoring and alerting implementations
- Configuration best practices

These patterns can be adapted and combined based on your specific application requirements and architecture.
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