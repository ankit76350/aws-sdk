package org.example.dto;

public class OrderRequest {

    private String userId;
    private String product;
    private int quantity;

    public String getUserId() { return userId; }
    public String getProduct() { return product; }
    public int getQuantity() { return quantity; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setProduct(String product) { this.product = product; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
