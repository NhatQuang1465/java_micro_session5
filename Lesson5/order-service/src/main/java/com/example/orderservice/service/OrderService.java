package com.example.orderservice.service;

import com.example.orderservice.dto.OrderRequestDTO;
import com.example.orderservice.dto.OrderResponseDTO;
import com.example.orderservice.dto.ProductDTO;
import com.example.orderservice.exception.BadRequestException;
import com.example.orderservice.exception.ResourceNotFoundException;
import com.example.orderservice.exception.ServiceUnavailableException;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;

    public OrderService(OrderRepository orderRepository,
                        RestTemplate restTemplate) {
        this.orderRepository = orderRepository;
        this.restTemplate = restTemplate;
    }


    public List<OrderResponseDTO> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    public OrderResponseDTO getOrderResponseById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        return mapToResponseDTO(order);
    }

    public List<OrderResponseDTO> getOrdersByCustomerId(Long customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    public ProductDTO getProductFromProductService(Long productId) {
        // Sử dụng URL với service name "product-service" thay vì IP/port cứng.
        // RestTemplate được đánh dấu @LoadBalanced sẽ tự động gửi request luân phiên (Load Balancing) tới các instances của product-service.
        String targetUrl = "http://product-service/api/v1/products/" + productId;

        try {
            ProductDTO product = restTemplate.getForObject(targetUrl, ProductDTO.class);
            if (product == null) {
                throw new ResourceNotFoundException("Product not found with id: " + productId);
            }
            return product;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        } catch (RestClientException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PRODUCT-SERVICE hiện không khả dụng"
            );
        }
    }

    @Transactional
    public OrderResponseDTO createOrder(OrderRequestDTO requestDTO) {
        if (requestDTO.getQuantity() == null || requestDTO.getQuantity() <= 0) {
            throw new BadRequestException("Quantity must be greater than 0");
        }

        // Lấy thông tin sản phẩm và giá từ Product Service qua DiscoveryClient
        ProductDTO product = getProductFromProductService(requestDTO.getProductId());
        BigDecimal unitPrice = product.getPrice();
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(requestDTO.getQuantity()));

        Order order = new Order();
        order.setCustomerId(requestDTO.getCustomerId());
        order.setProductId(requestDTO.getProductId());
        order.setQuantity(requestDTO.getQuantity());
        order.setOrderDate(LocalDateTime.now());
        order.setTotalAmount(totalAmount);
        order.setStatus("CREATED");

        Order savedOrder = orderRepository.save(order);
        return mapToResponseDTO(savedOrder);
    }

    private OrderResponseDTO mapToResponseDTO(Order order) {
        return new OrderResponseDTO(
                order.getId(),
                order.getCustomerId(),
                order.getProductId(),
                order.getQuantity(),
                order.getOrderDate(),
                order.getTotalAmount(),
                order.getStatus()
        );
    }
}
