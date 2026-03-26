package com.lin.spring.elasticsearch.mapper;

import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.entity.ProductDocument;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductDocument toDocument(Product product) {
        if (product == null) {
            return null;
        }
        ProductDocument doc = new ProductDocument();
        doc.setId(product.getId() != null ? product.getId().toString() : null);
        doc.setName(product.getName());
        doc.setDescription(product.getDescription());
        doc.setPrice(product.getPrice());
        doc.setCategory(product.getCategory());
        doc.setTags(product.getTagsAsList());
        doc.setCreatedAt(product.getCreatedAt());
        return doc;
    }

    public Product toEntity(ProductDocument doc) {
        if (doc == null) {
            return null;
        }
        Product product = new Product();
        if (doc.getId() != null) {
            try {
                product.setId(Long.parseLong(doc.getId()));
            } catch (NumberFormatException e) {
                // ID will be null for new documents
            }
        }
        product.setName(doc.getName());
        product.setDescription(doc.getDescription());
        product.setPrice(doc.getPrice());
        product.setCategory(doc.getCategory());
        product.setTagsFromList(doc.getTags());
        product.setCreatedAt(doc.getCreatedAt());
        return product;
    }
}
