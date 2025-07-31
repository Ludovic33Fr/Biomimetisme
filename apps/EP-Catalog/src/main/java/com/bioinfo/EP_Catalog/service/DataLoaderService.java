package com.bioinfo.EP_Catalog.service;

import com.bioinfo.EP_Catalog.model.Product;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class DataLoaderService {
    
    private List<Product> products;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostConstruct
    public void loadData() {
        try {
            ClassPathResource resource = new ClassPathResource("data/products.json");
            InputStream inputStream = resource.getInputStream();
            products = objectMapper.readValue(inputStream, new TypeReference<List<Product>>() {});
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors du chargement des données", e);
        }
    }
    
    public List<Product> getAllProducts() {
        return products;
    }
    
    public void reloadData() {
        loadData();
    }
} 