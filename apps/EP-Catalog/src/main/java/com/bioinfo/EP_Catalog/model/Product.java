package com.bioinfo.EP_Catalog.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Product {
    private String id;
    private String name;
    private String description;
    private double price;
    private String category;
    private String brand;
    private String color;
    private String size;
}