package com.example.userservice.repository;

import com.example.userservice.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    public Optional<Client> findBySubKey(String subKey);

    /**
     * Inserts the client, or updates it if {@code sub_key} already exists, in one atomic statement.
     * Postgres serializes concurrent calls for the same sub_key on uk_client_sub_key, so there is no race to handle.
     *
     * @return {@code true} if the row was inserted, {@code false} if an existing row was updated
     * ({@code xmax = 0} only holds for a freshly inserted row version)
     */
    @Query(value = """
            insert into client (id,sub_key, name, surname, email, phone_number, address,profile_key)
            values (:id,:subKey,
                    cast(:firstName as varchar), cast(:lastName as varchar), cast(:email as varchar),
                    cast(:phoneNumber as varchar), cast(:address as text), cast(:profileKey as varchar))
            on conflict (sub_key) do update
                set name         = excluded.name,
                    surname      = excluded.surname,
                    email        = excluded.email,
                    phone_number = excluded.phone_number,
                    address      = excluded.address
            returning (xmax = 0)
            """, nativeQuery = true)
    boolean upsertBySubKey(@Param("id") Long id,
                           @Param("subKey") String subKey,
                           @Param("firstName") String firstName,
                           @Param("lastName") String lastName,
                           @Param("email") String email,
                           @Param("phoneNumber") String phoneNumber,
                           @Param("address") String address,
                           @Param("profileKey") String profileKey);

    @Query(value = "select nextval('client_sequence')", nativeQuery = true)
    long nextId();


    @Query("select c.subKey from Client c where c.profileKey = :profileKey")
    Optional<String> getSubKeyByProfileKey(@Param("profileKey") String profileKey);
}
