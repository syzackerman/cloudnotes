package com.cloudnotes.service;

import com.cloudnotes.config.PaginationProperties;
import com.cloudnotes.exception.BadRequestException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class PageableValidator {

    private final PaginationProperties paginationProperties;

    public PageableValidator(PaginationProperties paginationProperties) {
        this.paginationProperties = paginationProperties;
    }

    public void validate(Pageable pageable) {
        if (pageable.getPageNumber() < 0) {
            throw new BadRequestException("Page number must not be negative");
        }
        if (pageable.getPageSize() < 1) {
            throw new BadRequestException("Page size must be at least 1");
        }
        if (pageable.getPageSize() > paginationProperties.maxSize()) {
            throw new BadRequestException("Page size must not exceed " + paginationProperties.maxSize());
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!paginationProperties.allowedSortFields().contains(order.getProperty())) {
                throw new BadRequestException("Unsupported sort field: " + order.getProperty());
            }
        }
    }
}
