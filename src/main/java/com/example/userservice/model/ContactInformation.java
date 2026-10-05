package com.example.userservice.model;

import jakarta.persistence.*;

@Embeddable
public record ContactInformation(String phoneNumber, String address) {

}
