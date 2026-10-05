package com.example.userservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "client")
public class Client {
    @Id
    @SequenceGenerator(
            name = "client_id_seq",
            sequenceName = "client_sequence",
            initialValue = 100000,
            allocationSize = 50
    )
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
    generator = "client_id_seq")
    private Long id;

    private String email;
    private String name;
    private String surname;
    @Embedded
    private ContactInformation contactInformation;
    @Column(unique = true)
    private String subKey;
    @Column(name = "profile_key", unique = true)
    private String profileKey;

}
