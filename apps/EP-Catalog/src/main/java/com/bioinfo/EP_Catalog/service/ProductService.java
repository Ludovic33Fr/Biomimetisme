package com.bioinfo.EP_Catalog.service;

import com.bioinfo.EP_Catalog.model.Product;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final DataLoaderService dataLoaderService;

    public ProductService(DataLoaderService dataLoaderService) {
        this.dataLoaderService = dataLoaderService;
    }

    public List<Product> getAllProducts() {
        return dataLoaderService.getAllProducts();
    }
    
    public Optional<Product> getProductById(String id) {
        return getAllProducts().stream()
            .filter(product -> product.getId().equals(id))
            .findFirst();
    }
}