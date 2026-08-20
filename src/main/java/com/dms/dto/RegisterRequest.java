package com.dms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The rules here mirror the Zod schema the registration form uses, because the
 * form is only the first of the two places that has to hold: anything posting
 * to /auth/register directly bypasses it entirely.
 */
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must be at most 50 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must be at most 50 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).+$",
             message = "Password must contain an uppercase letter, a lowercase letter and a number")
    private String password;

    @NotBlank(message = "Phone number is required")
    // Local (0771234567) or with the country code (+94771234567). The previous
    // rule was any 10-12 digits, which accepted numbers that cannot be dialled.
    @Pattern(regexp = "^(?:\\+94|0)(?:7\\d{8}|[1-9]\\d{8})$",
             message = "Enter a Sri Lankan number, e.g. 0771234567 or +94771234567")
    private String phone;

    public RegisterRequest() {}

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }
}