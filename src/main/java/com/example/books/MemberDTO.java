package com.example.books;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MemberDTO {
    private Long id;

    @NotBlank(message = "Code is required")
    @Size(max = 64, message = "Code must be at most 64 characters")
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    @NotBlank(message = "Status is required")
    private String status = "ACTIVE";

    @NotNull(message = "Loan limit is required")
    @Min(value = 1, message = "Loan limit must be at least 1")
    private Integer maxLoanLimit = 5;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getMaxLoanLimit() { return maxLoanLimit; }
    public void setMaxLoanLimit(Integer maxLoanLimit) { this.maxLoanLimit = maxLoanLimit; }
}