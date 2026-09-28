package com.example.customerservice.service;

import com.example.customerservice.dto.CustomerRequestDTO;
import com.example.customerservice.dto.CustomerResponseDTO;
import com.example.customerservice.dto.LoginRequestDTO;
import com.example.customerservice.entity.Customer;
import com.example.customerservice.exception.BadRequestException;
import com.example.customerservice.exception.ResourceNotFoundException;
import com.example.customerservice.repository.CustomerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public CustomerResponseDTO registerCustomer(CustomerRequestDTO requestDTO) {
        if (customerRepository.existsByEmail(requestDTO.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        Customer customer = new Customer();
        customer.setFullName(requestDTO.getFullName());
        customer.setEmail(requestDTO.getEmail());
        customer.setPassword(passwordEncoder.encode(requestDTO.getPassword()));

        Customer savedCustomer = customerRepository.save(customer);
        return mapToResponseDTO(savedCustomer);
    }

    public CustomerResponseDTO getCustomerById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
        return mapToResponseDTO(customer);
    }

    public CustomerResponseDTO login(LoginRequestDTO loginDTO) {
        Customer customer = customerRepository.findByEmail(loginDTO.getEmail())
                .orElseThrow(() -> new BadRequestException("email or password incorrect"));

        if (!passwordEncoder.matches(loginDTO.getPassword(), customer.getPassword())) {
            throw new BadRequestException("email or password incorrect");
        }

        return mapToResponseDTO(customer);
    }

    private CustomerResponseDTO mapToResponseDTO(Customer customer) {
        return new CustomerResponseDTO(
                customer.getId(),
                customer.getFullName(),
                customer.getEmail()
        );
    }
}
